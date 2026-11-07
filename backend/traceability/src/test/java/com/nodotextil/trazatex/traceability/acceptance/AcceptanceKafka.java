package com.nodotextil.trazatex.traceability.acceptance;

import com.nodotextil.trazatex.sharedtesting.TrazatexKafkaContainer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.testcontainers.kafka.KafkaContainer;

import java.util.List;
import java.util.Map;

/**
 * El Kafka de los escenarios: uno por JVM. Los tópicos que traceability consume los crean "los otros servicios" (aquí, esta clase):
 * el broker no los crea solo y traceability tampoco.
 */
final class AcceptanceKafka {

    static final KafkaContainer CONTAINER = new TrazatexKafkaContainer();
    static final KafkaProducer<String, String> PRODUCER;

    static {
        CONTAINER.start();
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", CONTAINER.getBootstrapServers()))) {
            admin.createTopics(List.of("orgaccess.company", "production.batch", "production.transformation", "production.transfer", "quality.control", "quality.failure").stream()
                    .map(name -> new NewTopic(name, 1, (short) 1)).toList()).all().get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        PRODUCER = new KafkaProducer<>(Map.<String, Object>of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, CONTAINER.getBootstrapServers(),
                ProducerConfig.ACKS_CONFIG, "all", ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }

    private AcceptanceKafka() {
    }
}
