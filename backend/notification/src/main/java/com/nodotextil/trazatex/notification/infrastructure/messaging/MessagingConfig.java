package com.nodotextil.trazatex.notification.infrastructure.messaging;

import com.nodotextil.trazatex.sharedmessaging.consumer.KafkaConsumerErrorHandling;
import com.nodotextil.trazatex.sharedmessaging.consumer.TopicWatcher;
import com.nodotextil.trazatex.sharedmessaging.producer.ReliableKafkaProducer;
import org.apache.kafka.clients.admin.NewTopic;
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
import java.time.Duration;
import java.util.Map;

@Configuration
@EnableKafka
@ConditionalOnProperty(name="trazatex.messaging.enabled",havingValue="true",matchIfMissing=true)
class MessagingConfig {
    @Bean
    ConsumerFactory<String, String> consumerFactory(@Value("${spring.kafka.bootstrap-servers}") String bootstrap) {
        return new DefaultKafkaConsumerFactory<>(Map.of(
                "bootstrap.servers", bootstrap, "auto.offset.reset", "earliest",
                "enable.auto.commit", false, "allow.auto.create.topics", false,
                "key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer",
                "value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer"));
    }

    @Bean
    KafkaAdmin kafkaAdmin(@Value("${spring.kafka.bootstrap-servers}") String bootstrap) {
        var admin = new KafkaAdmin(Map.of("bootstrap.servers", bootstrap));
        // Sin broker al arrancar el servicio sí arranca: los contenedores no empiezan hasta que el vigilante vea los tópicos
        admin.setFatalIfBrokerNotAvailable(false);
        admin.setOperationTimeout(10);
        return admin;
    }

    /** Los {@code .DLT} de lo que se consume (los declara el consumidor, ver docs/conventions.md); los tópicos de origen son ajenos y no se crean. */
    @Bean
    KafkaAdmin.NewTopics deadLetterTopics(@Value("${trazatex.messaging.partitions:1}") int partitions,
                                          @Value("${trazatex.messaging.replicas:1}") int replicas) {
        return new KafkaAdmin.NewTopics(ConsumedTopics.ALL.stream()
                .map(source -> TopicBuilder.name(source.topic() + KafkaConsumerErrorHandling.DLT_SUFFIX).partitions(partitions).replicas(replicas).build())
                .toArray(NewTopic[]::new));
    }

    /** Arranca cada contenedor cuando su tópico ajeno existe (lo programa {@link TopicWatcherJob}). */
    @Bean
    TopicWatcher topicWatcher(KafkaListenerEndpointRegistry registry, KafkaAdmin kafkaAdmin) {
        return new TopicWatcher(registry, kafkaAdmin, ConsumedTopics.ALL);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            @Value("${spring.kafka.bootstrap-servers}") String bootstrap,
            @Value("${trazatex.messaging.consumer.retry.max-retries:5}") int maxRetries,
            @Value("${trazatex.messaging.consumer.retry.initial-interval:PT1S}") Duration initialInterval,
            @Value("${trazatex.messaging.consumer.retry.max-interval:PT30S}") Duration maxInterval) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(KafkaConsumerErrorHandling.deadLetterErrorHandler(
                ReliableKafkaProducer.kafkaTemplate(bootstrap),
                new KafkaConsumerErrorHandling.RetrySettings(maxRetries, initialInterval, 2.0, maxInterval)));
        return factory;
    }
}
