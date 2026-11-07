package com.nodotextil.trazatex.production.integration;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import com.nodotextil.trazatex.sharedtesting.TrazatexKafkaContainer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los tres tópicos que Production produce no pueden perder historia por antigüedad: Traceability reconstruye la genealogía
 * leyéndolos desde el inicio. Caso de tu Kafka local: los tópicos <b>ya existen</b> con la retención por defecto (7 días) y con
 * datos; al arrancar el servicio quedan sin borrado por tiempo, sin perder mensajes ni cambiar las particiones.
 */
@SpringBootTest(properties = {"trazatex.messaging.enabled=true", "trazatex.cloudinary.url=", "trazatex.evidence.upload.enabled=false"})
class EventTopicsRetentionIT extends IntegrationTestBase {

    static final KafkaContainer KAFKA = new TrazatexKafkaContainer();
    static final List<String> TOPICS = List.of("production.batch", "production.transformation", "production.transfer");
    static final String PRE_EXISTING_KEY = UUID.randomUUID().toString();

    static {
        KAFKA.start();
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(TOPICS.stream().map(name -> new NewTopic(name, 1, (short) 1)).toList()).all().get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.<String, Object>of(
                "bootstrap.servers", KAFKA.getBootstrapServers(), "key.serializer", StringSerializer.class.getName(),
                "value.serializer", StringSerializer.class.getName()))) {
            for (String topic : TOPICS) {
                producer.send(new ProducerRecord<>(topic, PRE_EXISTING_KEY, "{\"previo\":true}")).get();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("trazatex.messaging.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Test
    void existingEventTopicsWithTheDefaultRetentionAreFixedAtStartupKeepingTheirDataAndPartitions() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            for (String topic : TOPICS) {
                var config = admin.describeConfigs(List.of(new ConfigResource(ConfigResource.Type.TOPIC, topic))).all().get().values().iterator().next();
                assertThat(config.get("retention.ms").value()).as("retención de %s", topic).isEqualTo("-1");
                assertThat(config.get("cleanup.policy").value()).as("%s sigue sin compactar", topic).isEqualTo("delete");
                assertThat(admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic).partitions()).as("particiones de %s", topic).hasSize(1);
            }
        }
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            for (String topic : TOPICS) {
                TopicPartition partition = new TopicPartition(topic, 0);
                consumer.assign(List.of(partition));
                consumer.seekToBeginning(List.of(partition));
                assertThat(consumer.poll(Duration.ofSeconds(5))).as("mensajes previos de %s", topic).extracting(r -> r.key()).contains(PRE_EXISTING_KEY);
            }
        }
    }
}
