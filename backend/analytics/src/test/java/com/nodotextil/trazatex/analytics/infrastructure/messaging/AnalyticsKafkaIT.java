package com.nodotextil.trazatex.analytics.infrastructure.messaging;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedtesting.TrazatexKafkaContainer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Analytics con Kafka real y PostgreSQL real (I13): los tópicos ajenos que aún no existen no se crean ni se consumen hasta que aparecen, cada
 * tópico se lee con su propio grupo (un mensaje que falla no frena a los demás), y lo ilegible o incompleto va a la {@code .DLT} de su tópico sin
 * dejar nada a medias.
 */
@SpringBootTest(properties = {
        "trazatex.messaging.enabled=true", "trazatex.messaging.consumer.topic-check-interval=PT1S",
        "trazatex.messaging.consumer.retry.max-retries=2", "trazatex.messaging.consumer.retry.initial-interval=PT0.2S",
        "trazatex.messaging.consumer.retry.max-interval=PT0.5S"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AnalyticsKafkaIT {
    static final KafkaContainer KAFKA = new TrazatexKafkaContainer();
    static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:16.15").withPassword(UUID.randomUUID().toString());
    static final ObjectMapper MAPPER = JsonMapper.builder().build();
    static final List<String> SOURCES = List.of("production.batch", "production.transformation", "production.transfer", "quality.control", "quality.failure");
    static final Map<String, String> GROUPS = Map.of(
            "production.batch", "analytics.production-batch", "production.transformation", "analytics.production-transformation",
            "production.transfer", "analytics.production-transfer", "quality.control", "analytics.quality-control", "quality.failure", "analytics.quality-failure");
    static { KAFKA.start(); DB.start(); }

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }

    @Autowired JdbcClient jdbc;
    @Autowired KafkaListenerEndpointRegistry registry;

    @Test @Order(1)
    void topicsOwnedByOtherServicesAreNeitherCreatedNorConsumedUntilTheyExist() throws Exception {
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            assertThat(topics()).doesNotContainAnyElementsOf(SOURCES);
            for (var source : ConsumedTopics.ALL) assertThat(registry.getListenerContainer(source.listenerId()).isRunning()).as(source.listenerId()).isFalse();
        });
        assertThat(topics()).containsAll(SOURCES.stream().map(topic -> topic + ".DLT").toList());       // lo único que declara son las .DLT
    }

    @Test @Order(2)
    void eachTopicStartsWhenItAppearsAndIsReadByItsOwnGroup() throws Exception {
        UUID company = UUID.randomUUID(), batch = UUID.randomUUID(), transfer = UUID.randomUUID(), failure = UUID.randomUUID(), transformation = UUID.randomUUID();
        try (AdminClient admins = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admins.createTopics(SOURCES.stream().map(topic -> new NewTopic(topic, 1, (short) 1)).toList()).all().get();
        }
        send("production.batch", batch.toString(), event("BatchRegistered", batch, Map.of("batchId", batch, "companyId", company)));
        send("production.transformation", transformation.toString(), event("TransformationCompleted", transformation, Map.of("companyId", company,
                "outputs", List.of(Map.of("batchId", UUID.randomUUID())), "shrinkageKg", 1, "wasteKg", 1, "startedAt", "2026-10-10T08:00:00Z", "completedAt", "2026-10-10T10:00:00Z")));
        send("production.transfer", transfer.toString(), event("TransferInitiated", transfer, Map.of("fromCompanyId", company, "toCompanyId", UUID.randomUUID())));
        send("quality.control", batch.toString(), event("QualityStatusChanged", batch, Map.of("batchId", batch, "companyId", company, "newStatus", "CONFORMING")));
        send("quality.failure", failure.toString(), event("QualityFailureUpdated", failure, Map.of("failureId", failure, "companyId", company, "cause", "Fibra corta", "status", "OPEN")));

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
            assertThat(jdbc.sql("SELECT count(*) FROM analytics_event WHERE company_id=:c").param("c", company).query(Long.class).single()).isEqualTo(5);
            assertThat(jdbc.sql("SELECT count(*) FROM batch_birth WHERE batch_id=:b").param("b", batch).query(Long.class).single()).isEqualTo(1);
        });
        try (AdminClient admins = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            for (var group : GROUPS.entrySet()) {
                Set<String> read = admins.listConsumerGroupOffsets(group.getValue()).partitionsToOffsetAndMetadata().get().keySet().stream()
                        .map(partition -> partition.topic()).collect(Collectors.toSet());
                assertThat(read).as(group.getValue()).containsExactly(group.getKey());
            }
        }
    }

    @Test @Order(3)
    void anUnreadableOrIncompleteMessageGoesToItsDeadLetterTopicWithoutBlockingOthersOrLeavingTraces() throws Exception {
        UUID company = UUID.randomUUID(), batch = UUID.randomUUID(), withoutVersion = UUID.randomUUID(), marker = UUID.randomUUID();
        send("production.transfer", "basura", "esto no es un evento");
        // BatchPhaseChanged sin batchVersion: ilegible para Analytics, y su fila en analytics_event se revierte con la transacción
        send("production.batch", batch.toString(), event("BatchPhaseChanged", withoutVersion, Map.of("batchId", batch, "companyId", company)));
        send("production.batch", marker.toString(), event("BatchRegistered", marker, Map.of("batchId", marker, "companyId", company)));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(jdbc.sql("SELECT count(*) FROM batch_birth WHERE batch_id=:b").param("b", marker).query(Long.class).single()).isEqualTo(1));
        assertThat(jdbc.sql("SELECT count(*) FROM analytics_event WHERE aggregate_id=:a").param("a", withoutVersion.toString()).query(Long.class).single()).isZero();
        assertThat(dead("production.transfer.DLT")).contains("esto no es un evento");
        assertThat(dead("production.batch.DLT").stream().filter(v -> v.contains(withoutVersion.toString())).count()).isEqualTo(1);
    }

    private List<String> dead(String topic) {
        var config = new HashMap<String, Object>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "prueba-dlt-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<String> values = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, String>(config)) {
            consumer.subscribe(List.of(topic));
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) values.add(record.value());
                return !values.isEmpty();
            });
        }
        return values;
    }
    private static Set<String> topics() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            return admin.listTopics().names().get();
        }
    }
    private static String event(String type, UUID aggregate, Map<String, Object> payload) {
        return MAPPER.writeValueAsString(BaseDomainEvent.of(type, aggregate.toString(), payload));
    }
    private static void send(String topic, String key, String value) throws Exception {
        try (var producer = new KafkaProducer<String, String>(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers(),
                "key.serializer", StringSerializer.class, "value.serializer", StringSerializer.class))) {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        }
    }
}
