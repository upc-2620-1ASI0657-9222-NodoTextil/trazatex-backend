package com.nodotextil.trazatex.sharedmessaging.producer;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

public final class ReliableKafkaProducer {

    private ReliableKafkaProducer() {
    }

    public static Map<String, Object> enforce(Map<String, Object> base) {
        Map<String, Object> config = new HashMap<>(base);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return config;
    }

    public static ProducerFactory<String, String> producerFactory(String bootstrapServers) {
        return producerFactory(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
    }

    public static ProducerFactory<String, String> producerFactory(Map<String, Object> base) {
        return new DefaultKafkaProducerFactory<>(enforce(base));
    }

    public static KafkaTemplate<String, String> kafkaTemplate(String bootstrapServers) {
        return new KafkaTemplate<>(producerFactory(bootstrapServers));
    }
}
