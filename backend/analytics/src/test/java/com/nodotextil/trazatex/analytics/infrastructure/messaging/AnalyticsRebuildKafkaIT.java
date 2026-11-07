package com.nodotextil.trazatex.analytics.infrastructure.messaging;

import com.nodotextil.trazatex.analytics.application.AnalyticsQueryService;
import com.nodotextil.trazatex.analytics.domain.model.AnalyticsSummary;
import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedmessaging.consumer.TopicWatcher;
import com.nodotextil.trazatex.sharedtesting.TrazatexKafkaContainer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * I10, la reconstrucción REAL de Analytics desde Kafka: con el servicio parado se vacía su base, se rebobinan sus cinco grupos al principio de cada
 * tópico y se vuelve a arrancar. El resultado tiene que ser idéntico —tablas fila por fila y los cuatro indicadores— al que había antes. Son los pasos
 * de {@code docs/operations/analytics-rebuild.md}, hechos con las mismas herramientas de Kafka que usaría quien opera el servicio.
 */
@SpringBootTest(properties = {
        "trazatex.messaging.enabled=true",
        // el vigilante no se programa solo: el servicio «parado» de la prueba no puede resucitar a mitad del procedimiento
        "trazatex.messaging.consumer.topic-check-interval=PT1H"})
class AnalyticsRebuildKafkaIT {
    static final KafkaContainer KAFKA = new TrazatexKafkaContainer();
    static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:16.15").withPassword(UUID.randomUUID().toString());
    static final ObjectMapper MAPPER = JsonMapper.builder().build();
    static { KAFKA.start(); DB.start(); }

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }

    @Autowired JdbcClient jdbc;
    @Autowired KafkaListenerEndpointRegistry registry;
    @Autowired TopicWatcher watcher;
    @Autowired AnalyticsQueryService queries;

    final UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    final Instant t = Instant.parse("2026-10-10T10:00:00Z");

    record Message(String topic, String key, String json) { }
    private Message message(String topic, String type, UUID aggregate, Map<String, Object> payload, long seconds) {
        return new Message(topic, aggregate.toString(), MAPPER.writeValueAsString(
                BaseDomainEvent.of(type, aggregate.toString(), payload, Clock.fixed(t.plusSeconds(seconds), ZoneOffset.UTC))));
    }
    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    /** Un mes de operación de dos empresas: nacimientos, división, transformación, cambios de dueño con la versión al revés del reloj, transferencias, controles y fallas. */
    private List<Message> history() {
        UUID lot1 = UUID.randomUUID(), lot2 = UUID.randomUUID(), child = UUID.randomUUID(), tr = UUID.randomUUID(), accepted = UUID.randomUUID(), rejected = UUID.randomUUID(),
                control = UUID.randomUUID(), failure1 = UUID.randomUUID(), failure2 = UUID.randomUUID();
        List<Message> m = new ArrayList<>();
        m.add(message("production.batch", "BatchRegistered", lot1, map("batchId", lot1, "companyId", a), 0));
        m.add(message("production.batch", "BatchRegistered", lot2, map("batchId", lot2, "companyId", a), 1));
        m.add(message("production.batch", "BatchSplit", lot1, map("parentBatchId", lot1, "companyId", a, "children", List.of(map("batchId", child, "qualityStatus", "NOT_REVIEWED"))), 5));
        m.add(message("production.batch", "BatchPhaseChanged", lot2, map("batchId", lot2, "companyId", b, "newPhase", "AVAILABLE", "qualityHold", false, "batchVersion", 4), 100));
        m.add(message("production.batch", "BatchPhaseChanged", lot2, map("batchId", lot2, "companyId", a, "newPhase", "AVAILABLE", "qualityHold", true, "batchVersion", 2), 900));
        m.add(message("production.transformation", "TransformationCompleted", tr, map("companyId", a, "outputs", List.of(map("batchId", UUID.randomUUID())),
                "shrinkageKg", 1.25, "wasteKg", 2.5, "startedAt", t.minusSeconds(7200), "completedAt", t), 10));
        m.add(message("production.transfer", "TransferInitiated", accepted, map("fromCompanyId", a, "toCompanyId", b), 20));
        m.add(message("production.transfer", "TransferAccepted", accepted, map("fromCompanyId", a, "toCompanyId", b, "acceptedAt", t.plusSeconds(10_820)), 10_820));
        m.add(message("production.transfer", "TransferInitiated", rejected, map("fromCompanyId", a, "toCompanyId", b), 30));
        m.add(message("production.transfer", "TransferRejected", rejected, map("fromCompanyId", a, "toCompanyId", b), 40));
        m.add(message("quality.control", "QualityStatusChanged", lot1, map("batchId", lot1, "companyId", a, "newStatus", "CONFORMING"), 60));
        m.add(message("quality.control", "QualityStatusChanged", lot2, map("batchId", lot2, "companyId", a, "newStatus", "FAILED"), 61));
        m.add(message("quality.control", "QualityControlCompleted", control, map("controlId", control, "companyId", a, "startedAt", t, "finishedAt", t.plusSeconds(3600)), 3_600));
        m.add(message("quality.failure", "QualityFailureUpdated", failure1, map("failureId", failure1, "companyId", a, "cause", null, "status", "OPEN"), 70));
        m.add(message("quality.failure", "QualityFailureUpdated", failure1, map("failureId", failure1, "companyId", a, "cause", "Fibra corta", "status", "OPEN"), 80));
        m.add(message("quality.failure", "QualityFailureUpdated", failure2, map("failureId", failure2, "companyId", a, "cause", "Manchas", "status", "CONFIRMED"), 90));
        m.add(m.get(0));                                                   // entrega repetida: Kafka garantiza «al menos una vez»
        return m;
    }

    private String table(String sql) {
        return jdbc.sql(sql).query(String.class).single();
    }
    /** Las cuatro tablas, fila por fila y ordenadas (menos {@code projected_at}, que es la hora de proyección), y los cuatro indicadores de cada empresa. */
    private List<Object> snapshot() {
        List<Object> snapshot = new ArrayList<>();
        snapshot.add(table("SELECT coalesce(string_agg(concat_ws('|',event_id,event_type,aggregate_id,occurred_at,company_id,business_at,payload::text),E'\\n' ORDER BY event_id),'') FROM analytics_event"));
        snapshot.add(table("SELECT coalesce(string_agg(concat_ws('|',batch_id,company_id,registered_at),E'\\n' ORDER BY batch_id),'') FROM batch_birth"));
        snapshot.add(table("SELECT coalesce(string_agg(concat_ws('|',event_id,batch_id,company_id,occurred_at,batch_version),E'\\n' ORDER BY event_id,batch_id),'') FROM batch_owner"));
        snapshot.add(table("SELECT coalesce(string_agg(concat_ws('|',event_id,batch_id,status,occurred_at),E'\\n' ORDER BY event_id,batch_id),'') FROM batch_quality"));
        for (UUID company : List.of(a, b)) {
            AnalyticsSummary s = queries.summary(company, "2026-10-01", "2026-10-31");
            snapshot.add(List.of(s.quality(), s.failureCauses(), s.durations(), s.materials()));
        }
        return snapshot;
    }
    private long events() { return jdbc.sql("SELECT count(*) FROM analytics_event").query(Long.class).single(); }

    @Test
    void rebuildingFromKafkaGivesTheSameResultAsBefore() throws Exception {
        List<Message> history = history();
        long distinct = history.stream().map(Message::json).distinct().count();
        List<String> topics = ConsumedTopics.ALL.stream().map(TopicWatcher.Source::topic).toList();
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(topics.stream().map(topic -> new NewTopic(topic, 1, (short) 1)).toList()).all().get();
        }
        try (var producer = new KafkaProducer<String, String>(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers(),
                "key.serializer", StringSerializer.class, "value.serializer", StringSerializer.class))) {
            for (Message m : history) producer.send(new ProducerRecord<>(m.topic(), m.key(), m.json())).get();
        }

        // 1. el servicio consume por primera vez
        watcher.startConsumersWhoseTopicExists();
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(events()).isEqualTo(distinct));
        List<Object> before = snapshot();
        AnalyticsSummary october = queries.summary(a, "2026-10-01", "2026-10-31");
        assertThat(october.quality()).as("la prueba parte de datos de verdad").isNotEmpty();
        assertThat(october.durations()).hasSize(3);
        assertThat(october.failureCauses()).hasSize(2);
        assertThat(october.materials().completedTransformations()).isEqualTo(1);

        // 2. se para el servicio y se vacía su base (solo la suya)
        registry.getListenerContainers().forEach(container -> container.stop());
        jdbc.sql("TRUNCATE analytics_event,batch_birth,batch_owner,batch_quality").update();
        assertThat(events()).isZero();
        assertThat(queries.summary(a, "2026-10-01", "2026-10-31").quality()).isEmpty();

        // 3. se rebobinan los cinco grupos al principio de su tópico (kafka-consumer-groups.sh --reset-offsets --to-earliest --execute)
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            for (var source : ConsumedTopics.ALL) {
                String group = groupOf(source);
                TopicPartition partition = new TopicPartition(source.topic(), 0);
                long earliest = admin.listOffsets(Map.of(partition, OffsetSpec.earliest())).all().get().get(partition).offset();
                await().atMost(Duration.ofSeconds(30)).ignoreExceptions().untilAsserted(() ->
                        admin.alterConsumerGroupOffsets(group, Map.of(partition, new OffsetAndMetadata(earliest))).all().get());
                assertThat(admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get().get(partition).offset()).as(group).isEqualTo(earliest);
            }
        }

        // 4. se arranca el servicio: lee todo otra vez
        watcher.startConsumersWhoseTopicExists();
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(events()).isEqualTo(distinct));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(snapshot()).isEqualTo(before));
        assertThat(queries.summary(a, "2026-10-01", "2026-10-31").quality()).isEqualTo(october.quality());
    }

    /** El grupo de cada contenedor, tal como lo declara su {@code @KafkaListener}. */
    private String groupOf(TopicWatcher.Source source) {
        return registry.getListenerContainer(source.listenerId()).getGroupId();
    }
}
