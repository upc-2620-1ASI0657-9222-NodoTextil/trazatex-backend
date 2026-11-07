package com.nodotextil.trazatex.traceability.infrastructure.config;

import com.nodotextil.trazatex.sharedkernel.outbox.EventPublisherPort;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxRelay;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxStorePort;
import com.nodotextil.trazatex.sharedmessaging.consumer.KafkaConsumerErrorHandling;
import com.nodotextil.trazatex.sharedmessaging.consumer.KafkaConsumerErrorHandling.RetrySettings;
import com.nodotextil.trazatex.sharedmessaging.consumer.TopicWatcher;
import com.nodotextil.trazatex.sharedmessaging.producer.KafkaEventPublisher;
import com.nodotextil.trazatex.sharedmessaging.producer.ReliableKafkaProducer;
import com.nodotextil.trazatex.traceability.infrastructure.messaging.ConsumedTopics;
import com.nodotextil.trazatex.traceability.infrastructure.messaging.ImpactDeadLetterReporter;
import com.nodotextil.trazatex.traceability.infrastructure.messaging.ProducedTopics;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
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
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Consumo de eventos (ADR-004): lectura idempotente, reintentos con espera creciente y {@code <tópico>.DLT}. Los tópicos
 * consumidos son de otros servicios y <b>no</b> se declaran aquí —ni se crean al suscribirse ({@code allow.auto.create.topics=false})—:
 * solo sus {@code .DLT}. Un grupo nuevo lee desde el principio ({@code earliest}) para reconstruir la genealogía con todo lo
 * ocurrido antes de su primer arranque. {@code trazatex.messaging.enabled=false} apaga todo esto (pruebas sin broker).
 */
@Configuration
@EnableKafka
@EnableScheduling
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

    /** Los {@code .DLT} de lo que se consume (los declara el consumidor, ver docs/conventions.md). */
    @Bean
    KafkaAdmin.NewTopics deadLetterTopics(@Value("${trazatex.messaging.partitions:1}") int partitions,
                                          @Value("${trazatex.messaging.replicas:1}") int replicas) {
        NewTopic[] topics = ConsumedTopics.ALL.stream()
                .map(source -> TopicBuilder.name(source.topic() + KafkaConsumerErrorHandling.DLT_SUFFIX).partitions(partitions).replicas(replicas).build())
                .toArray(NewTopic[]::new);
        return new KafkaAdmin.NewTopics(topics);
    }

    /** Arranca cada contenedor cuando su tópico ajeno existe (lo programa {@code TopicWatcherJob}). */
    @Bean
    TopicWatcher topicWatcher(KafkaListenerEndpointRegistry registry, KafkaAdmin kafkaAdmin) {
        return new TopicWatcher(registry, kafkaAdmin, ConsumedTopics.ALL);
    }

    /**
     * {@code traceability.impact}: el único tópico que Traceability produce ({@code PossibleDerivedFailureDetected}, clave {@code failureId}). Sin borrado por tiempo
     * ({@code retention.ms=-1}) como los demás tópicos de eventos de negocio: quien llegue o se reconstruya después (Quality, Notification, Analytics) lee las fallas
     * desde el inicio. Con {@code modify-topic-configs=true} (local) también corrige uno que ya existía con otra retención.
     */
    @Bean
    NewTopic impactTopic(@Value("${trazatex.messaging.partitions:1}") int partitions, @Value("${trazatex.messaging.replicas:1}") int replicas) {
        return TopicBuilder.name(ProducedTopics.IMPACT).partitions(partitions).replicas(replicas).config(TopicConfig.RETENTION_MS_CONFIG, "-1").build();
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

    @Bean
    OutboxRelay outboxRelay(OutboxStorePort store, EventPublisherPort publisher, PlatformTransactionManager transactionManager, Clock clock,
                            @Value("${trazatex.messaging.relay-batch-size:100}") int batchSize) {
        return new OutboxRelay(store, publisher, transactionManager, clock, batchSize);
    }

    /**
     * {@code max.poll.interval.ms} explícito (por omisión 5 min, el de Kafka): lo que pasa entre dos {@code poll()} sin que el consumidor sea expulsado del grupo. Un
     * reintento espera dentro del hilo del consumidor y vuelve a leer el mensaje, así que lo que importa es que <b>una espera</b> quepa con holgura (la más larga de
     * los reintentos del análisis de impacto es de 60 s: una quinta parte). Se valida al arrancar (ver {@link #kafkaListenerContainerFactory}).
     */
    @Bean
    ConsumerFactory<String, String> traceabilityConsumerFactory(@Value("${trazatex.messaging.bootstrap-servers}") String bootstrapServers,
                                                                @Value("${trazatex.messaging.consumer.max-poll-interval:PT5M}") Duration maxPollInterval) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, (int) maxPollInterval.toMillis());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> traceabilityConsumerFactory, KafkaTemplate<String, String> outboxKafkaTemplate,
            @Value("${trazatex.messaging.consumer.retry.max-retries:5}") int maxRetries,
            @Value("${trazatex.messaging.consumer.retry.initial-interval:PT1S}") Duration initialInterval,
            @Value("${trazatex.messaging.consumer.retry.multiplier:2.0}") double multiplier,
            @Value("${trazatex.messaging.consumer.retry.max-interval:PT30S}") Duration maxInterval,
            @Value("${trazatex.messaging.consumer.impact-retry.max-retries:10}") int impactMaxRetries,
            @Value("${trazatex.messaging.consumer.impact-retry.initial-interval:PT1S}") Duration impactInitialInterval,
            @Value("${trazatex.messaging.consumer.impact-retry.multiplier:2.0}") double impactMultiplier,
            @Value("${trazatex.messaging.consumer.impact-retry.max-interval:PT60S}") Duration impactMaxInterval,
            @Value("${trazatex.messaging.consumer.max-poll-interval:PT5M}") Duration maxPollInterval) {
        requireWaitsThatFitInThePollInterval(impactMaxInterval, maxPollInterval);
        RetrySettings impactRetry = new RetrySettings(impactMaxRetries, impactInitialInterval, impactMultiplier, impactMaxInterval);
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(traceabilityConsumerFactory);
        // Una familia incompleta se resuelve cuando llegan los eventos de Production: más reintentos y más espera que el resto; agotados, DLT con un ERROR
        factory.setCommonErrorHandler(KafkaConsumerErrorHandling.deadLetterErrorHandler(outboxKafkaTemplate,
                new RetrySettings(maxRetries, initialInterval, multiplier, maxInterval),
                exception -> ImpactDeadLetterReporter.causeOf(exception) != null ? impactRetry : null,
                new ImpactDeadLetterReporter(impactMaxRetries + 1)));
        return factory;
    }

    /**
     * Una espera de reintento ocurre entre dos {@code poll()}: si fuera de la mitad de {@code max.poll.interval.ms} (o más), un reintento lento o un mensaje largo
     * podrían sacar al consumidor del grupo y provocar un rebalanceo. Con los valores por omisión (60 s contra 5 min) sobra: es una quinta parte. Si alguien sube
     * la espera o baja el intervalo hasta romper esa proporción, el servicio no arranca.
     */
    static void requireWaitsThatFitInThePollInterval(Duration longestWait, Duration maxPollInterval) {
        if (longestWait.multipliedBy(2).compareTo(maxPollInterval) > 0) {
            throw new IllegalStateException("La espera más larga de los reintentos del análisis de impacto (" + longestWait + ") debe ser como máximo la mitad de "
                    + "max.poll.interval.ms (" + maxPollInterval + "): si no, el consumidor puede ser expulsado del grupo y provocar un rebalanceo");
        }
    }
}
