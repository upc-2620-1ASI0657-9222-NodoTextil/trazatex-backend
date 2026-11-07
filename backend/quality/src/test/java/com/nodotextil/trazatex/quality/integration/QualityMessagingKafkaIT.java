package com.nodotextil.trazatex.quality.integration;

import com.nodotextil.trazatex.quality.application.port.out.BatchProjectionRepositoryPort;
import com.nodotextil.trazatex.quality.domain.model.BatchPhase;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.quality.domain.model.QualityStatus;
import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedtesting.TrazatexKafkaContainer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Mensajería de Quality con Kafka real: sus cuatro tópicos quedan sin borrado por tiempo y sin compactar (también uno que ya existía
 * compactado), los tópicos de Production que aún no existen no se crean ni se consumen hasta que aparecen, y la proyección de lotes
 * se alimenta de {@code production.batch}, {@code production.transformation} y {@code production.transfer} sin importar el orden, sin
 * aplicar dos veces el mismo evento y mandando lo ilegible a la DLT.
 */
@SpringBootTest(properties = {
        "trazatex.messaging.enabled=true", "trazatex.quality.start.job-enabled=false",
        "trazatex.messaging.consumer.topic-check-interval=PT1S",
        "trazatex.messaging.consumer.retry.max-retries=2",
        "trazatex.messaging.consumer.retry.initial-interval=PT0.2S",
        "trazatex.messaging.consumer.retry.max-interval=PT0.5S"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QualityMessagingKafkaIT extends IntegrationTestBase {

    static final KafkaContainer KAFKA = new TrazatexKafkaContainer();
    static final ObjectMapper MAPPER = JsonMapper.builder().build();
    static final String BATCH_TOPIC = "production.batch";
    static final String TRANSFORMATION_TOPIC = "production.transformation";
    static final String TRANSFER_TOPIC = "production.transfer";
    static final List<String> QUALITY_TOPICS = List.of("quality.control", "quality.failure", "quality.control.DLT", "quality.failure.DLT");
    static final UUID COMPANY = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    static {
        KAFKA.start();
        // Estado previo de un Kafka local: quality.control ya existe, compactado y con datos; quality.failure.DLT con la configuración por defecto
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic("quality.control", 1, (short) 1).configs(Map.of("cleanup.policy", "compact")),
                    new NewTopic("quality.failure.DLT", 1, (short) 1))).all().get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("trazatex.messaging.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    KafkaListenerEndpointRegistry registry;
    @Autowired
    BatchProjectionRepositoryPort batches;
    @Autowired
    com.nodotextil.trazatex.quality.application.port.in.RecoverPendingCommandsUseCase recovery;
    @Autowired
    com.nodotextil.trazatex.quality.application.port.out.ImpactedBatchRepositoryPort notices;
    @Autowired
    com.nodotextil.trazatex.quality.application.port.out.QualityEventPublisherPort events;
    @Autowired
    org.springframework.transaction.PlatformTransactionManager transactionManager;

    // ---- Kafka ----

    record Sent(UUID eventId, String json) {
    }

    static Sent envelope(String type, String aggregateId, Map<String, Object> payload) {
        BaseDomainEvent event = BaseDomainEvent.of(type, aggregateId, payload);
        return new Sent(event.eventId(), MAPPER.writeValueAsString(event));
    }

    static void send(String topic, String key, String json) {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.<String, Object>of(
                "bootstrap.servers", KAFKA.getBootstrapServers(), "key.serializer", StringSerializer.class.getName(),
                "value.serializer", StringSerializer.class.getName()))) {
            producer.send(new ProducerRecord<>(topic, key, json)).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static void createTopics(String... topics) {
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(java.util.Arrays.stream(topics).map(name -> new NewTopic(name, 1, (short) 1)).toList()).all().get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Sent registered(UUID batch, UUID company) {
        return envelope("BatchRegistered", batch.toString(), Map.of("batchId", batch, "traceabilityId", "TT-ABCDEFGHJK", "companyId", company,
                "materialType", "Algodón", "quantityKg", 100));
    }

    static Sent phaseChanged(UUID batch, UUID company, String newPhase, boolean hold, long version) {
        return envelope("BatchPhaseChanged", batch.toString(), Map.of("batchId", batch, "companyId", company, "previousPhase", "AVAILABLE",
                "newPhase", newPhase, "qualityHold", hold, "reason", "TEST", "requestId", UUID.randomUUID().toString(), "batchVersion", version));
    }

    BatchProjection row(UUID batch) {
        return batches.findById(batch).orElseThrow();
    }

    boolean running(String listenerId) {
        return registry.getListenerContainer(listenerId).isRunning();
    }

    long processedEvents(UUID eventId) {
        return jdbc.sql("SELECT count(*) FROM processed_events WHERE event_id = :id").param("id", eventId).query(Long.class).single();
    }

    // ---- Los tópicos de Quality y los ajenos ----

    @Test
    @Order(1)
    void qualityTopicsAreDeclaredWithoutRetentionByTimeAndWithoutCompactionEvenWhenOneAlreadyExistedCompacted() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            Set<String> existing = admin.listTopics().names().get();
            assertThat(existing).containsAll(QUALITY_TOPICS);
            for (String topic : QUALITY_TOPICS) {
                var config = admin.describeConfigs(List.of(new ConfigResource(ConfigResource.Type.TOPIC, topic))).all().get().values().iterator().next();
                assertThat(config.get("retention.ms").value()).as("retención de %s", topic).isEqualTo("-1");
                assertThat(config.get("cleanup.policy").value()).as("%s no se compacta", topic).isEqualTo("delete");
                assertThat(admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic).partitions()).as("particiones de %s", topic).hasSize(1);
            }
            assertThat(existing).as("Quality declara las DLT de lo que consume, nunca los tópicos de Production")
                    .contains("production.batch.DLT", "production.transformation.DLT", "production.transfer.DLT")
                    .doesNotContain(BATCH_TOPIC, TRANSFORMATION_TOPIC, TRANSFER_TOPIC);
        }
    }

    @Test
    @Order(2)
    void consumersOfTopicsThatDoNotExistYetStayStoppedAndStartWhenTheTopicsAppear() throws Exception {
        for (String listener : List.of("quality-production-batch", "quality-production-transformation", "quality-production-transfer")) {
            assertThat(running(listener)).as(listener).isFalse();
        }
        Thread.sleep(2500);   // dos pasadas del vigilante (1 s) y siguen detenidos: no se suscribieron a un tópico ausente
        assertThat(running("quality-production-batch")).isFalse();

        createTopics(BATCH_TOPIC, TRANSFORMATION_TOPIC, TRANSFER_TOPIC);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(running("quality-production-batch")).isTrue();
            assertThat(running("quality-production-transformation")).isTrue();
            assertThat(running("quality-production-transfer")).isTrue();
        });
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            assertThat(admin.listTopics().names().get()).contains(BATCH_TOPIC);
        }
    }

    // ---- La proyección ----

    @Test
    @Order(3)
    void aBatchPhaseChangeThatArrivesBeforeTheBatchRegistrationIsCompletedByIt() {
        UUID batch = UUID.randomUUID();

        send(BATCH_TOPIC, batch.toString(), phaseChanged(batch, COMPANY, "IN_TRANSFER", false, 2).json());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(batches.findById(batch)).isPresent());
        assertThat(row(batch).qualityStatus()).as("aún se desconoce").isNull();

        send(BATCH_TOPIC, batch.toString(), registered(batch, COMPANY).json());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(row(batch).qualityStatus()).isEqualTo(QualityStatus.NOT_REVIEWED));
        assertThat(row(batch).phase()).isEqualTo(BatchPhase.IN_TRANSFER);
        assertThat(row(batch).batchVersion()).isEqualTo(2);
    }

    @Test
    @Order(4)
    void olderPhaseChangesNeverRollTheBatchBackAndTheSameEventIsAppliedOnce() {
        UUID batch = UUID.randomUUID();
        Sent newest = phaseChanged(batch, OTHER, "AVAILABLE", true, 5);
        send(BATCH_TOPIC, batch.toString(), registered(batch, COMPANY).json());
        send(BATCH_TOPIC, batch.toString(), newest.json());
        send(BATCH_TOPIC, batch.toString(), phaseChanged(batch, COMPANY, "IN_QUALITY_CONTROL", false, 3).json());
        send(BATCH_TOPIC, batch.toString(), newest.json());   // el mismo evento, repetido

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(processedEvents(newest.eventId())).isEqualTo(1));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(jdbc.sql("SELECT count(*) FROM processed_events").query(Long.class).single()).isGreaterThanOrEqualTo(3));

        BatchProjection result = row(batch);
        assertThat(result.phase()).isEqualTo(BatchPhase.AVAILABLE);
        assertThat(result.companyId()).isEqualTo(OTHER);
        assertThat(result.qualityHold()).isTrue();
        assertThat(result.batchVersion()).isEqualTo(5);
    }

    @Test
    @Order(5)
    void splitChildrenAndTransformationOutputsAreKnownFromTheirOwnEvents() {
        UUID parent = UUID.randomUUID();
        UUID conformingChild = UUID.randomUUID();
        UUID freshChild = UUID.randomUUID();
        UUID output = UUID.randomUUID();
        send(BATCH_TOPIC, parent.toString(), envelope("BatchSplit", parent.toString(), Map.of("parentBatchId", parent, "companyId", COMPANY,
                "children", List.of(Map.of("batchId", conformingChild, "qualityStatus", "CONFORMING", "materialType", "Algodón"),
                        Map.of("batchId", freshChild, "qualityStatus", "NOT_REVIEWED", "materialType", "Algodón")))).json());
        send(TRANSFORMATION_TOPIC, UUID.randomUUID().toString(), envelope("TransformationCompleted", UUID.randomUUID().toString(), Map.of(
                "transformationId", UUID.randomUUID(), "type", "WEAVING", "companyId", OTHER, "inputs", List.of(),
                "outputs", List.of(Map.of("batchId", output, "traceabilityId", "TT-ZZZZZZZZZZ", "finalProduct", false)))).json());

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(batches.findById(conformingChild)).isPresent();
            assertThat(batches.findById(freshChild)).isPresent();
            assertThat(batches.findById(output)).isPresent();
        });
        assertThat(row(conformingChild).qualityStatus()).isEqualTo(QualityStatus.CONFORMING);
        assertThat(row(conformingChild).everControlled()).isTrue();
        assertThat(row(freshChild).qualityStatus()).isEqualTo(QualityStatus.NOT_REVIEWED);
        assertThat(row(freshChild).everControlled()).isFalse();
        assertThat(row(output).companyId()).isEqualTo(OTHER);
        assertThat(row(output).phase()).isEqualTo(BatchPhase.AVAILABLE);
    }

    @Test
    @Order(6)
    void anAcceptedTransferMovesTheBatchToTheReceivingCompany() {
        UUID batch = UUID.randomUUID();
        send(BATCH_TOPIC, batch.toString(), registered(batch, COMPANY).json());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(batches.findById(batch)).isPresent());

        send(TRANSFER_TOPIC, UUID.randomUUID().toString(), envelope("TransferAccepted", UUID.randomUUID().toString(), Map.of(
                "transferId", UUID.randomUUID(), "batchIds", List.of(batch), "fromCompanyId", COMPANY, "toCompanyId", OTHER,
                "acceptedAt", "2026-10-23T12:00:00Z")).json());

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(row(batch).companyId()).isEqualTo(OTHER));
    }

    @Test
    @Order(7)
    void eventsThatAreNotQualitysAreIgnoredWithoutALeftoverRow() {
        UUID batch = UUID.randomUUID();
        Sent foreign = envelope("BatchSomethingNew", batch.toString(), Map.of("batchId", batch));
        Sent last = registered(batch, COMPANY);

        send(BATCH_TOPIC, batch.toString(), foreign.json());
        send(BATCH_TOPIC, batch.toString(), last.json());

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(processedEvents(last.eventId())).isEqualTo(1));
        assertThat(processedEvents(foreign.eventId())).as("se filtra antes del consumo idempotente").isZero();
    }

    @Test
    @Order(8)
    void anUnreadableEventGoesStraightToTheDeadLetterTopicWithItsKey() {
        UUID batch = UUID.randomUUID();
        Sent withoutCompany = envelope("BatchRegistered", batch.toString(), Map.of("batchId", batch));

        send(BATCH_TOPIC, batch.toString(), withoutCompany.json());

        ConsumerRecord<String, String> dead = readFirst("production.batch.DLT", batch.toString());
        assertThat(dead.value()).isEqualTo(withoutCompany.json());
        assertThat(batches.findById(batch)).isEmpty();
        assertThat(processedEvents(withoutCompany.eventId())).isZero();
    }

    // ---- 06c: avisos de Traceability y publicación del outbox ----

    static Sent detected(UUID failure, UUID source, UUID... affected) {
        return envelope("PossibleDerivedFailureDetected", failure.toString(), Map.of("failureId", failure, "sourceBatchId", source,
                "affected", java.util.Arrays.stream(affected).map(batch -> Map.of("batchId", batch, "companyId", COMPANY, "commonAncestorBatchId", source)).toList()));
    }

    @Test
    @Order(9)
    void theImpactTopicOfTraceabilityIsWatchedAndItsNoticesAreRecordedOnceWhateverTheDuplicatesAndTheMalformedGoToTheDlt() {
        assertThat(running("quality-traceability-impact")).as("traceability.impact aún no existe").isFalse();
        createTopics("traceability.impact");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(running("quality-traceability-impact")).isTrue());
        UUID batch = UUID.randomUUID();
        batches.insert(BatchProjection.restore(batch, COMPANY, BatchPhase.AVAILABLE, false, QualityStatus.NOT_REVIEWED, false, 1, java.time.Instant.parse("2026-10-23T12:00:00Z")));
        UUID failure = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        Sent notice = detected(failure, source, batch);

        send("traceability.impact", failure.toString(), notice.json());
        send("traceability.impact", failure.toString(), notice.json());   // el mismo evento otra vez

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(notices.findByFailure(failure)).hasSize(1));
        assertThat(notices.findByFailure(failure).get(0).lockStatus()).isEqualTo(com.nodotextil.trazatex.quality.domain.model.LockStatus.PENDING);
        assertThat(notices.findByFailure(failure).get(0).impactType()).isEqualTo(com.nodotextil.trazatex.quality.domain.model.ImpactType.DESCENDANT);
        assertThat(processedEvents(notice.eventId())).isEqualTo(1);
        assertThat(batches.findById(batch).orElseThrow().qualityStatus()).as("el consumidor no cambia la calidad: lo hace el HOLD confirmado").isEqualTo(QualityStatus.NOT_REVIEWED);

        UUID bad = UUID.randomUUID();
        Sent malformed = envelope("PossibleDerivedFailureDetected", bad.toString(), Map.of("failureId", bad, "sourceBatchId", source, "affected", List.of()));
        send("traceability.impact", bad.toString(), malformed.json());
        assertThat(readFirst("traceability.impact.DLT", bad.toString()).value()).isEqualTo(malformed.json());
        assertThat(notices.findByFailure(bad)).isEmpty();
    }

    @Test
    @Order(10)
    void theOutboxEventsReachTheQualityTopicsWithTheirKeys() {
        UUID batch = UUID.randomUUID();
        batches.insert(BatchProjection.restore(batch, COMPANY, BatchPhase.AVAILABLE, false, QualityStatus.NOT_REVIEWED, false, 1, java.time.Instant.parse("2026-10-23T12:00:00Z")));
        UUID failure = UUID.randomUUID();
        ProductionStub.answersOk();
        send("traceability.impact", failure.toString(), detected(failure, UUID.randomUUID(), batch).json());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(notices.findByFailure(failure)).hasSize(1));

        assertThat(recovery.recoverPendingHolds()).isEqualTo(1);

        ConsumerRecord<String, String> statusChanged = readFirst("quality.control", batch.toString());
        JsonNode envelope = MAPPER.readTree(statusChanged.value());
        assertThat(envelope.path("type").asString()).isEqualTo("QualityStatusChanged");
        assertThat(envelope.path("payload").path("newStatus").asString()).isEqualTo("POTENTIAL_DERIVED_FAILURE");
        assertThat(envelope.path("payload").path("controlId").isNull()).isTrue();

        UUID confirmedFailure = UUID.randomUUID();
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx -> events.publish(
                new com.nodotextil.trazatex.quality.domain.event.FailureConfirmed(confirmedFailure, batch, UUID.randomUUID(), COMPANY, "Humedad",
                        new java.math.BigDecimal("12.5"), java.time.Instant.parse("2026-10-23T12:00:00Z"))));
        ConsumerRecord<String, String> confirmed = readFirst("quality.failure", confirmedFailure.toString());
        assertThat(MAPPER.readTree(confirmed.value()).path("type").asString()).isEqualTo("FailureConfirmed");
        assertThat(MAPPER.readTree(confirmed.value()).path("payload").path("cause").asString()).isEqualTo("Humedad");
    }

    private static ConsumerRecord<String, String> readFirst(String topic, String key) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));
            List<ConsumerRecord<String, String>> found = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (key.equals(r.key())) {
                        found.add(r);
                    }
                });
                assertThat(found).isNotEmpty();
            });
            return found.get(0);
        }
    }
}
