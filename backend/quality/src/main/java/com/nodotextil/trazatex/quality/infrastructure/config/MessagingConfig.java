package com.nodotextil.trazatex.quality.infrastructure.config;

import com.nodotextil.trazatex.quality.infrastructure.messaging.ConsumedTopics;
import com.nodotextil.trazatex.quality.infrastructure.messaging.Topics;
import com.nodotextil.trazatex.sharedkernel.outbox.EventPublisherPort;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxRelay;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxStorePort;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.sharedmessaging.consumer.KafkaConsumerErrorHandling;
import com.nodotextil.trazatex.sharedmessaging.consumer.KafkaConsumerErrorHandling.RetrySettings;
import com.nodotextil.trazatex.sharedmessaging.consumer.ProcessedEventPort;
import com.nodotextil.trazatex.sharedmessaging.consumer.TopicWatcher;
import com.nodotextil.trazatex.sharedmessaging.producer.KafkaEventPublisher;
import com.nodotextil.trazatex.sharedmessaging.producer.ReliableKafkaProducer;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mensajería de Quality (ADR-004): los tópicos que declara, el consumo idempotente con reintentos y {@code <tópico>.DLT}.
 * Los tópicos de Production que consume <b>no</b> se declaran aquí —ni se crean al suscribirse ({@code allow.auto.create.topics=false})—:
 * solo sus {@code .DLT}. Un grupo nuevo lee desde el principio ({@code earliest}). {@code trazatex.messaging.enabled=false} apaga todo
 * esto (pruebas sin broker).
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class MessagingConfig {

    @Bean
    KafkaAdmin kafkaAdmin(@Value("${trazatex.messaging.bootstrap-servers}") String bootstrapServers,
                          @Value("${trazatex.messaging.modify-topic-configs:true}") boolean modifyTopicConfigs) {
        KafkaAdmin admin = new KafkaAdmin(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
        admin.setModifyTopicConfigs(modifyTopicConfigs);
        // Sin broker al arrancar el servicio sí arranca: los contenedores no empiezan hasta que el vigilante vea los tópicos
        admin.setFatalIfBrokerNotAvailable(false);
        admin.setOperationTimeout(10);
        return admin;
    }

    /**
     * Los tópicos de Quality y sus {@code .DLT}: {@code retention.ms=-1} (sin borrado por tiempo) y {@code cleanup.policy=delete} (sin
     * compactar). Varios tipos de evento comparten la clave y cada uno es un hecho de la historia; Production, Traceability, Notification
     * y Analytics leen desde el inicio al arrancar por primera vez o al reconstruirse. Con {@code modify-topic-configs=true} (local)
     * también corrige los que ya existían con otra configuración. Production y Traceability declaran las mismas {@code .DLT} como
     * consumidores: es idempotente.
     *
     * <p>Azure Event Hubs Standard retiene como máximo 7 días y su retención se fija al crear el event hub (ver docs/progress.md).
     */
    @Bean
    KafkaAdmin.NewTopics qualityTopics(@Value("${trazatex.messaging.partitions:1}") int partitions, @Value("${trazatex.messaging.replicas:1}") int replicas) {
        return new KafkaAdmin.NewTopics(List.of(Topics.CONTROL, Topics.FAILURE).stream()
                .flatMap(name -> java.util.stream.Stream.of(name, name + KafkaConsumerErrorHandling.DLT_SUFFIX))
                .map(name -> TopicBuilder.name(name).partitions(partitions).replicas(replicas)
                        .config(TopicConfig.RETENTION_MS_CONFIG, "-1").config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE).build())
                .toArray(NewTopic[]::new));
    }

    /** Los {@code .DLT} de lo que se consume (los declara el consumidor, ver docs/conventions.md). */
    @Bean
    KafkaAdmin.NewTopics deadLetterTopics(@Value("${trazatex.messaging.partitions:1}") int partitions, @Value("${trazatex.messaging.replicas:1}") int replicas) {
        return new KafkaAdmin.NewTopics(ConsumedTopics.ALL.stream()
                .map(source -> TopicBuilder.name(source.topic() + KafkaConsumerErrorHandling.DLT_SUFFIX).partitions(partitions).replicas(replicas).build())
                .toArray(NewTopic[]::new));
    }

    /** Arranca cada contenedor cuando su tópico ajeno existe (lo programa {@code TopicWatcherJob}). */
    @Bean
    TopicWatcher topicWatcher(KafkaListenerEndpointRegistry registry, KafkaAdmin kafkaAdmin) {
        return new TopicWatcher(registry, kafkaAdmin, ConsumedTopics.ALL);
    }

    /** Lo usa la DLT y el relay del outbox: el mismo productor fiable ({@code acks=all}, idempotente). */
    @Bean
    KafkaTemplate<String, String> outboxKafkaTemplate(@Value("${trazatex.messaging.bootstrap-servers}") String bootstrapServers) {
        return ReliableKafkaProducer.kafkaTemplate(bootstrapServers);
    }

    @Bean
    EventPublisherPort kafkaEventPublisher(KafkaTemplate<String, String> outboxKafkaTemplate) {
        return new KafkaEventPublisher(outboxKafkaTemplate);
    }

    /** Publica el outbox en Kafka después del commit (ADR-004); el {@code @Scheduled} de ~1 s es {@code OutboxRelayJob}. */
    @Bean
    OutboxRelay outboxRelay(OutboxStorePort store, EventPublisherPort publisher, PlatformTransactionManager transactionManager, Clock clock,
                            @Value("${trazatex.messaging.relay-batch-size:100}") int batchSize) {
        return new OutboxRelay(store, publisher, transactionManager, clock, batchSize);
    }

    @Bean
    IdempotentEventHandler idempotentEventHandler(ProcessedEventPort processedEvents, PlatformTransactionManager transactionManager,
                                                  ObjectMapper objectMapper) {
        return new IdempotentEventHandler(processedEvents, transactionManager, objectMapper);
    }

    @Bean
    ConsumerFactory<String, String> qualityConsumerFactory(@Value("${trazatex.messaging.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> qualityConsumerFactory, KafkaTemplate<String, String> outboxKafkaTemplate,
            @Value("${trazatex.messaging.consumer.retry.max-retries:5}") int maxRetries,
            @Value("${trazatex.messaging.consumer.retry.initial-interval:PT1S}") Duration initialInterval,
            @Value("${trazatex.messaging.consumer.retry.multiplier:2.0}") double multiplier,
            @Value("${trazatex.messaging.consumer.retry.max-interval:PT30S}") Duration maxInterval) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(qualityConsumerFactory);
        // Lo que falla se repite con espera creciente y, agotados los reintentos, va a <tópico>.DLT
        factory.setCommonErrorHandler(KafkaConsumerErrorHandling.deadLetterErrorHandler(outboxKafkaTemplate,
                new RetrySettings(maxRetries, initialInterval, multiplier, maxInterval)));
        return factory;
    }
}
