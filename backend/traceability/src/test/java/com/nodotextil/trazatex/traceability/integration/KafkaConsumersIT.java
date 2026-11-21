package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.traceability.integration.EventMessages.Message;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"trazatex.messaging.enabled=true", "trazatex.messaging.consumer.topic-check-interval=PT0.5S",
        "trazatex.messaging.consumer.retry.max-retries=2", "trazatex.messaging.consumer.retry.initial-interval=PT30S",
        "trazatex.messaging.consumer.retry.max-interval=PT60S"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KafkaConsumersIT extends IntegrationTestBase {

    static final KafkaContainer KAFKA = KafkaTestSupport.startKafka();
    static final KafkaProducer<String, String> PRODUCER;
    static final Duration PATIENCE = Duration.ofSeconds(60);
    static final OrderScenario.Instance HISTORY = OrderScenario.small(1, null);
    static final List<Message> IGNORED = new ArrayList<>();

    static {
        KafkaTestSupport.createTopics(KAFKA.getBootstrapServers(), Map.of("production.batch", 1, "production.transformation", 1,
                "production.transfer", 1, "orgaccess.company", 1));
        PRODUCER = KafkaTestSupport.producer(KAFKA.getBootstrapServers());
        UUID anyCompany = UUID.randomUUID();
        IGNORED.add(EventMessages.of("orgaccess.company", anyCompany.toString(), UUID.randomUUID(), "CompanyStatusChanged",
                EventMessages.map("companyId", anyCompany, "status", "ACTIVE")));
        IGNORED.add(EventMessages.of("orgaccess.company", anyCompany.toString(), UUID.randomUUID(), "CompanyStatusChanged",
                EventMessages.map("companyId", anyCompany, "status", "INACTIVE")));
        IGNORED.add(EventMessages.of("production.transfer", "tr-x", UUID.randomUUID(), "TransferInitiated",
                EventMessages.map("transferId", UUID.randomUUID(), "batchIds", List.of(UUID.randomUUID()))));
        IGNORED.add(EventMessages.of("production.batch", "b-x", UUID.randomUUID(), "UnTipoDeEventoDelFuturo", EventMessages.map("x", 1)));
        IGNORED.forEach(message -> KafkaTestSupport.send(PRODUCER, message));
        OrderScenario.NAMED_ORDERS.get(0).forEach(name -> KafkaTestSupport.send(PRODUCER, HISTORY.messages().get(name)));
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("trazatex.messaging.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Override
    @BeforeEach
    void emptyGraph() {
    }

    private static String bootstrap() {
        return KAFKA.getBootstrapServers();
    }

    private List<ConsumerRecord<String, String>> deadLetters(String topic, Duration wait) {
        return KafkaTestSupport.readAll(bootstrap(), topic + ".DLT", wait);
    }

    @Test
    @Order(1)
    void whatWasPublishedBeforeTheServiceStartedIsReadFromTheBeginningAndBuildsTheGraph() {
        GraphAssertions.awaitProcessed(neo4j, HISTORY.eventIds(), PATIENCE);

        GraphAssertions.assertExpectedGraph(neo4j, HISTORY);
    }

    @Test
    @Order(2)
    void typesTraceabilityDoesNotHandleAreIgnoredWithoutDltOrProcessedEvent() {
        List<UUID> ignored = IGNORED.stream().map(Message::eventId).toList();

        assertThat(GraphAssertions.processed(neo4j, ignored)).as("no dejan fila en :ProcessedEvent").isZero();
        for (String topic : List.of("orgaccess.company", "production.batch", "production.transformation", "production.transfer")) {
            assertThat(deadLetters(topic, Duration.ofSeconds(1))).as("la DLT de " + topic + " está vacía").isEmpty();
        }
        assertThat(neo4j.query("MATCH (c:Company {companyId: $id}) RETURN count(c) AS n").bind(IGNORED.get(0).key()).to("id").fetch().one().orElseThrow())
                .as("un CompanyStatusChanged no crea nada (CompanyRegistered sí se consume: guarda el nombre)").containsEntry("n", 0L);
    }

    @Test
    @Order(3)
    void theServiceDeclaresOnlyTheDeadLetterTopicsOfWhatItConsumesAndDoesNotCreateTheForeignOnes(CapturedOutput output) throws Exception {
        Thread.sleep(2000);

        Set<String> topics = KafkaTestSupport.topics(bootstrap());

        assertThat(topics).contains("orgaccess.company.DLT", "production.batch.DLT", "production.transformation.DLT", "production.transfer.DLT",
                "quality.control.DLT", "quality.failure.DLT", "traceability.impact");
        assertThat(topics).as("quality.control y quality.failure son de Quality: traceability no los crea").doesNotContain("quality.control", "quality.failure");
        assertThat(output.getAll()).as("suscribirse a un tópico ausente escribiría un aviso por segundo").doesNotContain("UNKNOWN_TOPIC_OR_PARTITION");
    }

    @Test
    @Order(4)
    void unreadableMessagesGoStraightToTheDltAndTheConsumerKeepsGoing() {
        UUID company = UUID.randomUUID();
        UUID batch = UUID.randomUUID();
        UUID goodEvent = UUID.randomUUID();
        Message noVersion = EventMessages.of("production.batch", batch.toString(), UUID.randomUUID(), "BatchPhaseChanged",
                EventMessages.map("batchId", batch, "companyId", company, "newPhase", "AVAILABLE", "qualityHold", false));
        Message noQr = EventMessages.of("production.batch", batch.toString(), UUID.randomUUID(), "BatchRegistered",
                EventMessages.map("batchId", batch, "traceabilityId", "TT-X", "companyId", company, "materialType", "Algodón",
                        "composition", EventMessages.cotton(), "quantityKg", 1, "createdAt", Instant.now()));
        Message unknownPhase = EventMessages.of("production.batch", batch.toString(), UUID.randomUUID(), "BatchPhaseChanged",
                EventMessages.map("batchId", batch, "companyId", company, "newPhase", "FLOTANDO", "qualityHold", false, "batchVersion", 1));
        KafkaTestSupport.sendRaw(PRODUCER, "production.batch", "roto", "esto no es un sobre");
        KafkaTestSupport.send(PRODUCER, noVersion);
        KafkaTestSupport.send(PRODUCER, noQr);
        KafkaTestSupport.send(PRODUCER, unknownPhase);
        Message good = EventMessages.phaseChanged(goodEvent, batch, company, "AVAILABLE", "PROCESSED", false, 5);
        KafkaTestSupport.send(PRODUCER, good);

        GraphAssertions.awaitProcessed(neo4j, List.of(goodEvent), Duration.ofSeconds(20));

        List<ConsumerRecord<String, String>> dlt = deadLetters("production.batch", Duration.ofSeconds(3));
        assertThat(dlt).extracting(ConsumerRecord::key).as("los cuatro ilegibles, con su clave").containsExactlyInAnyOrder("roto", batch.toString(),
                batch.toString(), batch.toString());
        assertThat(dlt).extracting(ConsumerRecord::value).anySatisfy(value -> assertThat(value).isEqualTo("esto no es un sobre"));
        assertThat(GraphAssertions.processed(neo4j, List.of(noVersion.eventId(), noQr.eventId(), unknownPhase.eventId()))).isZero();
        assertThat(neo4j.query("MATCH (b:Batch {batchId: $id}) RETURN b.phase AS phase, b.batchVersion AS v").bind(batch.toString()).to("id").fetch().one()
                .orElseThrow()).containsEntry("phase", "PROCESSED").containsEntry("v", 5L);
    }

    @Test
    @Order(5)
    void theSameEventDeliveredTwiceIsAppliedOnce() {
        UUID company = UUID.randomUUID();
        UUID batch = UUID.randomUUID();
        Message registered = EventMessages.batchRegistered(UUID.randomUUID(), batch, "TT-DUP-1", "QR-DUP-1", company, "Algodón", 10, "Piura", "Textiles", "Seco", Instant.now());
        Message changed = EventMessages.phaseChanged(UUID.randomUUID(), batch, company, "AVAILABLE", "IN_TRANSFORMATION", false, 1);

        for (Message message : List.of(registered, registered, changed, changed, registered)) {
            KafkaTestSupport.send(PRODUCER, message);
        }

        GraphAssertions.awaitProcessed(neo4j, List.of(registered.eventId(), changed.eventId()), PATIENCE);
        assertThat(GraphAssertions.processed(neo4j, List.of(registered.eventId(), changed.eventId()))).isEqualTo(2);
        assertThat(neo4j.query("MATCH (b:Batch {batchId: $id}) RETURN count(b) AS n, b.phase AS phase").bind(batch.toString()).to("id").fetch().one().orElseThrow())
                .containsEntry("n", 1L).containsEntry("phase", "IN_TRANSFORMATION");
        assertThat(deadLetters("production.batch", Duration.ofSeconds(1))).hasSize(4);
    }

    @Test
    @Order(6)
    void qualityControlIsConsumedAsSoonAsItAppears(CapturedOutput output) {
        UUID knownBatch = HISTORY.messages().get("BR(L1)").key().isEmpty() ? null : UUID.fromString(HISTORY.messages().get("BR(L1)").key());
        assertThat(KafkaTestSupport.topics(bootstrap())).doesNotContain("quality.control");
        assertThat(output.getAll()).doesNotContain("UNKNOWN_TOPIC_OR_PARTITION");

        KafkaTestSupport.createTopics(bootstrap(), Map.of("quality.control", 1));
        Message changed = EventMessages.qualityStatusChanged(UUID.randomUUID(), knownBatch, UUID.randomUUID(), "NOT_REVIEWED", "CONFORMING");
        Message ignored = EventMessages.of("quality.control", UUID.randomUUID().toString(), UUID.randomUUID(), "QualityControlCompleted",
                EventMessages.map("controlId", UUID.randomUUID(), "batchId", knownBatch, "result", "CONFORMING"));
        KafkaTestSupport.send(PRODUCER, ignored);
        KafkaTestSupport.send(PRODUCER, changed);

        GraphAssertions.awaitProcessed(neo4j, List.of(changed.eventId()), Duration.ofSeconds(30));
        assertThat(neo4j.query("MATCH (b:Batch {batchId: $id}) RETURN b.qualityStatus AS status").bind(knownBatch.toString()).to("id").fetch().one().orElseThrow())
                .containsEntry("status", "CONFORMING");
        assertThat(GraphAssertions.processed(neo4j, List.of(ignored.eventId()))).as("QualityControlCompleted es de analytics").isZero();
        assertThat(deadLetters("quality.control", Duration.ofSeconds(1))).isEmpty();
    }

    @Test
    @Order(7)
    void everyConsumedTopicHasItsOwnGroup() {
        assertThat(KafkaTestSupport.consumerGroups(bootstrap())).contains("traceability.orgaccess-company", "traceability.production-batch",
                "traceability.production-transformation", "traceability.production-transfer", "traceability.quality-control");
    }

    @Test
    @Order(8)
    void fiveConsumersTouchingTheSameCompaniesAndBatchesAtOnceNeverSendAnythingToTheDlt(CapturedOutput output) {
        UUID cA = UUID.randomUUID();
        UUID cB = UUID.randomUUID();
        int batches = 40;
        List<Message> burst = new ArrayList<>();
        List<UUID> batchIds = IntStream.range(0, batches).mapToObj(i -> UUID.randomUUID()).toList();
        Instant now = Instant.parse("2026-10-21T10:00:00Z");
        for (int i = 0; i < batches; i++) {
            UUID batch = batchIds.get(i);
            burst.add(EventMessages.batchRegistered(UUID.randomUUID(), batch, "TT-STRESS-" + i, "QR-STRESS-" + i, cA, "Algodón", 10, "Piura", "Textiles", "Seco", now));
            burst.add(EventMessages.phaseChanged(UUID.randomUUID(), batch, cB, "IN_TRANSFER", "AVAILABLE", false, 2));
            burst.add(EventMessages.transferAccepted(UUID.randomUUID(), UUID.randomUUID(), List.of(batch), cA, cB, now));
            burst.add(EventMessages.qualityStatusChanged(UUID.randomUUID(), batch, cA, "NOT_REVIEWED", i % 2 == 0 ? "CONFORMING" : "FAILED"));
        }
        for (int i = 0; i < 15; i++) {
            burst.add(EventMessages.transformationCompleted(UUID.randomUUID(), UUID.randomUUID(), "DYEING", cA, "Cardadora", "Cardado",
                    List.of(batchIds.get(2 * i), batchIds.get(2 * i + 1)), UUID.randomUUID(), "TT-OUT-" + i, "QR-OUT-" + i, "Tela", 5, false, 1, "Orillo", 1, now, now));
        }
        for (int i = 0; i < 6; i++) {
            burst.add(EventMessages.companyPrivacyUpdated(UUID.randomUUID(), i % 2 == 0 ? cA : cB, Map.of("SUPPLIER", i % 3 == 0 ? "SHARED" : "PRIVATE")));
        }
        Collections.shuffle(burst, new java.util.Random(7));
        List<UUID> ids = burst.stream().map(Message::eventId).toList();

        KafkaTestSupport.burst(PRODUCER, burst);

        GraphAssertions.awaitProcessed(neo4j, ids, PATIENCE);
        for (String topic : List.of("orgaccess.company", "production.batch", "production.transformation", "production.transfer", "quality.control")) {
            assertThat(deadLetters(topic, Duration.ofMillis(500)).stream().filter(r -> burst.stream().anyMatch(m -> m.json().equals(r.value()))).toList())
                    .as("nada de la ráfaga va a la DLT de " + topic).isEmpty();
        }
        List<String> idTexts = batchIds.stream().map(UUID::toString).toList();
        assertThat(neo4j.query("MATCH (c:Company {companyId: $cB})-[:OWNS_BATCH]->(b:Batch) WHERE b.batchId IN $ids RETURN count(b) AS n")
                .bind(cB.toString()).to("cB").bind(idTexts).to("ids").fetch().one().orElseThrow()).as("los 40 acabaron con la empresa B").containsEntry("n", 40L);
        assertThat(neo4j.query("MATCH (:Company)-[r:OWNS_BATCH]->(b:Batch) WHERE b.batchId IN $ids RETURN count(r) AS n")
                .bind(idTexts).to("ids").fetch().one().orElseThrow()).as("una sola relación de dueño por lote").containsEntry("n", 40L);
        assertThat(neo4j.query("MATCH (b:Batch) WHERE b.batchId IN $ids AND (b.provisional OR b.phase <> 'AVAILABLE') RETURN count(b) AS n")
                .bind(idTexts).to("ids").fetch().one().orElseThrow()).containsEntry("n", 0L);
        System.out.printf("ESTRÉS: %d eventos, %d repeticiones por fallo transitorio de Neo4j%n", ids.size(),
                output.getAll().split("Fallo transitorio de Neo4j", -1).length - 1);
    }

    @Test
    @Order(9)
    void aCompanyRegisteredFillsTheNameAndNeverTouchesThePrivacyInEitherOrder() {
        UUID privacyFirst = UUID.randomUUID();
        UUID registeredFirst = UUID.randomUUID();
        Message privacyBefore = EventMessages.companyPrivacyUpdated(UUID.randomUUID(), privacyFirst, Map.of("SUPPLIER", "SHARED"));
        Message nameAfter = EventMessages.of("orgaccess.company", privacyFirst.toString(), UUID.randomUUID(), "CompanyRegistered",
                EventMessages.map("companyId", privacyFirst, "ruc", "20131312955", "businessName", "Hilandería Andina SAC", "activities", List.of("SPINNING"), "status", "ACTIVE"));
        Message nameBefore = EventMessages.of("orgaccess.company", registeredFirst.toString(), UUID.randomUUID(), "CompanyRegistered",
                EventMessages.map("companyId", registeredFirst, "ruc", "20100047218", "businessName", "Tintorería del Sur SAC", "activities", List.of("DYEING"), "status", "ACTIVE"));
        Message privacyAfter = EventMessages.companyPrivacyUpdated(UUID.randomUUID(), registeredFirst, Map.of("ORIGIN", "SHARED"));

        for (Message message : List.of(privacyBefore, nameAfter, nameBefore, privacyAfter)) {
            KafkaTestSupport.send(PRODUCER, message);
        }

        GraphAssertions.awaitProcessed(neo4j, List.of(privacyBefore.eventId(), nameAfter.eventId(), nameBefore.eventId(), privacyAfter.eventId()), PATIENCE);
        Map<String, Object> first = neo4j.query("MATCH (c:Company {companyId: $id}) RETURN properties(c) AS p").bind(privacyFirst.toString()).to("id").fetch().one()
                .map(row -> (Map<String, Object>) row.get("p")).orElseThrow();
        Map<String, Object> second = neo4j.query("MATCH (c:Company {companyId: $id}) RETURN properties(c) AS p").bind(registeredFirst.toString()).to("id").fetch().one()
                .map(row -> (Map<String, Object>) row.get("p")).orElseThrow();
        assertThat(first).containsEntry("businessName", "Hilandería Andina SAC");
        assertThat(first.get("privacy").toString()).contains("\"SUPPLIER\":\"SHARED\"");
        assertThat(second).containsEntry("businessName", "Tintorería del Sur SAC");
        assertThat(second.get("privacy").toString()).contains("\"ORIGIN\":\"SHARED\"");
        assertThat(deadLetters("orgaccess.company", Duration.ofSeconds(1))).isEmpty();
    }
}
