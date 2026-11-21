package com.nodotextil.trazatex.traceability.integration;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.testcontainers.kafka.KafkaContainer;
import com.nodotextil.trazatex.sharedtesting.TrazatexKafkaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

final class KafkaTestSupport {

    private KafkaTestSupport() {
    }

    static KafkaContainer startKafka() {
        KafkaContainer kafka = new TrazatexKafkaContainer();
        kafka.start();
        return kafka;
    }

    static void createTopics(String bootstrap, Map<String, Integer> partitionsByTopic) {
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", bootstrap))) {
            admin.createTopics(partitionsByTopic.entrySet().stream().map(e -> new NewTopic(e.getKey(), e.getValue(), (short) 1)).toList()).all().get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Set<String> topics(String bootstrap) {
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", bootstrap))) {
            return admin.listTopics().names().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Set<String> consumerGroups(String bootstrap) {
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", bootstrap))) {
            return admin.listConsumerGroups().all().get(10, TimeUnit.SECONDS).stream().map(g -> g.groupId()).collect(Collectors.toSet());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static KafkaProducer<String, String> producer(String bootstrap) {
        return new KafkaProducer<>(Map.<String, Object>of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap, ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }

    static void send(KafkaProducer<String, String> producer, EventMessages.Message message) {
        sendRaw(producer, message.topic(), message.key(), message.json());
    }

    static void sendRaw(KafkaProducer<String, String> producer, String topic, String key, String value) {
        try {
            producer.send(new ProducerRecord<>(topic, key, value)).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo publicar en " + topic, e);
        }
    }

    static void burst(KafkaProducer<String, String> producer, List<EventMessages.Message> messages) {
        messages.forEach(message -> producer.send(new ProducerRecord<>(message.topic(), message.key(), message.json())));
        producer.flush();
    }

    static List<ConsumerRecord<String, String>> readAll(String bootstrap, String topic, Duration wait) {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.<String, Object>of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ConsumerConfig.GROUP_ID_CONFIG, "prueba-" + UUID.randomUUID(), ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false, ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class, ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(topic));
            long until = System.nanoTime() + wait.toNanos();
            while (System.nanoTime() < until) {
                consumer.poll(Duration.ofMillis(300)).forEach(records::add);
            }
        }
        return records;
    }
}
