package com.nodotextil.trazatex.production.infrastructure.config;

import com.nodotextil.trazatex.production.infrastructure.messaging.ConsumedTopics;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.sharedmessaging.consumer.KafkaConsumerErrorHandling;
import com.nodotextil.trazatex.sharedmessaging.consumer.KafkaConsumerErrorHandling.RetrySettings;
import com.nodotextil.trazatex.sharedmessaging.consumer.ProcessedEventPort;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Consumo de eventos (ADR-004): lectura idempotente, reintentos con espera creciente y {@code <tópico>.DLT}. Los tópicos
 * consumidos son de otros servicios y <b>no</b> se declaran aquí —ni se crean al suscribirse (`allow.auto.create.topics=false`)—:
 * solo sus DLT. Un grupo nuevo lee desde el principio ({@code earliest}) para conocer lo ocurrido antes de su primer arranque.
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class MessagingConsumerConfig {

    @Bean
    NewTopic orgaccessCompanyDeadLetterTopic(@Value("${trazatex.messaging.partitions:1}") int partitions,
                                             @Value("${trazatex.messaging.replicas:1}") int replicas) {
        return TopicBuilder.name(ConsumedTopics.ORGACCESS_COMPANY + KafkaConsumerErrorHandling.DLT_SUFFIX)
                .partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic qualityControlDeadLetterTopic(@Value("${trazatex.messaging.partitions:1}") int partitions,
                                           @Value("${trazatex.messaging.replicas:1}") int replicas) {
        return TopicBuilder.name(ConsumedTopics.QUALITY_CONTROL + KafkaConsumerErrorHandling.DLT_SUFFIX)
                .partitions(partitions).replicas(replicas).build();
    }

    @Bean
    IdempotentEventHandler idempotentEventHandler(ProcessedEventPort processedEvents, PlatformTransactionManager transactionManager,
                                                  ObjectMapper objectMapper) {
        return new IdempotentEventHandler(processedEvents, transactionManager, objectMapper);
    }

    @Bean
    ConsumerFactory<String, String> productionConsumerFactory(@Value("${trazatex.messaging.bootstrap-servers}") String bootstrapServers) {
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
            ConsumerFactory<String, String> productionConsumerFactory, KafkaTemplate<String, String> outboxKafkaTemplate,
            @Value("${trazatex.messaging.consumer.retry.max-retries:5}") int maxRetries,
            @Value("${trazatex.messaging.consumer.retry.initial-interval:PT1S}") Duration initialInterval,
            @Value("${trazatex.messaging.consumer.retry.multiplier:2.0}") double multiplier,
            @Value("${trazatex.messaging.consumer.retry.max-interval:PT30S}") Duration maxInterval) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(productionConsumerFactory);
        // Los reintentos y la DLT: lo que falla se repite con espera creciente; un conflicto de versión (@Version) es un fallo
        // reintentable más, a propósito
        factory.setCommonErrorHandler(KafkaConsumerErrorHandling.deadLetterErrorHandler(outboxKafkaTemplate,
                new RetrySettings(maxRetries, initialInterval, multiplier, maxInterval)));
        return factory;
    }
}
