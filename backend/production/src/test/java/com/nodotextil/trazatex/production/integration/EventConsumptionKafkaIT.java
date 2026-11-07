package com.nodotextil.trazatex.production.integration;

import com.nodotextil.trazatex.production.application.port.in.ApplyQualityStatusUseCase;
import com.nodotextil.trazatex.production.application.port.out.BatchRepositoryPort;
import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.kafka.KafkaContainer;
import com.nodotextil.trazatex.sharedtesting.TrazatexKafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Eventos de entrada con Kafka real (ADR-004, RF-003): la proyección de empresas se alimenta de {@code orgaccess.company} (desde
 * el principio del tópico), {@code QualityStatusChanged} copia el estado de calidad desde {@code quality.control} —un tópico de
 * otro servicio que al arrancar no existe—, el mismo evento dos veces no se aplica dos veces y un conflicto de {@code @Version}
 * con una operación del usuario se reintenta en lugar de ir a la DLT.
 */
@SpringBootTest(properties = {
        "trazatex.messaging.enabled=true", "trazatex.cloudinary.url=", "trazatex.evidence.upload.enabled=false",
        "trazatex.messaging.consumer.quality.topic-check-interval=PT1S",
        "trazatex.messaging.consumer.retry.max-retries=3",
        "trazatex.messaging.consumer.retry.initial-interval=PT0.2S",
        "trazatex.messaging.consumer.retry.max-interval=PT0.5S"})
@ExtendWith(OutputCaptureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventConsumptionKafkaIT extends ApiTestBase {

    static final KafkaContainer KAFKA = new TrazatexKafkaContainer();
    static final ObjectMapper MAPPER = JsonMapper.builder().build();
    static final String COMPANY_TOPIC = "orgaccess.company";
    static final String QUALITY_TOPIC = "quality.control";

    /** Una empresa registrada ANTES de que Production arrancara por primera vez. */
    static final UUID PRE_EXISTING_COMPANY = UUID.randomUUID();

    static {
        KAFKA.start();
        // Estado previo: organization-access ya creó su tópico y publicó un alta; quality.control NO existe (no hay creación automática)
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(COMPANY_TOPIC, 1, (short) 1))).all().get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        send(COMPANY_TOPIC, PRE_EXISTING_COMPANY.toString(), registered(PRE_EXISTING_COMPANY, "Hilos del Norte SAC", "ACTIVE").json());
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("trazatex.messaging.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("trazatex.messaging.relay-delay", () -> "PT0.2S");
    }

    @MockitoSpyBean
    ApplyQualityStatusUseCase qualityStatus;
    @MockitoSpyBean
    BatchRepositoryPort batchRepository;

    /** No se siembran las empresas A y B: la proyección de esta prueba viene solo de Kafka. */
    @Override
    @BeforeEach
    void seedCompanies() {
    }

    /** Como la base, pero sin vaciar la proyección de empresas ni los eventos procesados: se alimentan de Kafka al arrancar. */
    @Override
    @BeforeEach
    void resetDatabase() {
        jdbc.sql("""
                TRUNCATE phase_command_log, transformation_outputs, transformation_inputs, transformations, transfer_batches,
                         transfers, batch_compositions, batches, machine_status_history, machines, outbox, idempotency_keys, audit_log
                """).update();
        clock.advance(Duration.between(clock.instant(), START));
    }

    // ---- Kafka ----

    record Sent(UUID eventId, String json) {
    }

    static Sent envelope(String type, String aggregateId, Map<String, Object> payload) {
        BaseDomainEvent event = BaseDomainEvent.of(type, aggregateId, payload);
        return new Sent(event.eventId(), MAPPER.writeValueAsString(event));
    }

    static Sent registered(UUID company, String name, String status) {
        return envelope("CompanyRegistered", company.toString(), Map.of("companyId", company, "ruc", "20100070970",
                "businessName", name, "activities", List.of("SPINNING"), "status", status));
    }

    static Sent statusChanged(UUID company, String status) {
        return envelope("CompanyStatusChanged", company.toString(), Map.of("companyId", company, "status", status));
    }

    static Sent qualityStatusChanged(UUID batch, UUID company, String previous, String next) {
        return envelope("QualityStatusChanged", batch.toString(), Map.of("batchId", batch, "companyId", company,
                "previousStatus", previous, "newStatus", next, "controlId", UUID.randomUUID()));
    }

    static void send(String topic, String key, String value) {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.<String, Object>of(
                "bootstrap.servers", KAFKA.getBootstrapServers(), "key.serializer", StringSerializer.class.getName(),
                "value.serializer", StringSerializer.class.getName()))) {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<ConsumerRecord<String, String>> readAll(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            if (consumer.partitionsFor(topic) == null) {
                return records;
            }
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));
            long end = consumer.endOffsets(List.of(partition)).get(partition);
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (consumer.position(partition) < end && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(300)).forEach(records::add);
            }
        }
        return records;
    }

    private static List<ConsumerRecord<String, String>> deadLetters(String topic, String key) {
        return readAll(topic + ".DLT").stream().filter(r -> key.equals(r.key())).toList();
    }

    private static boolean topicExists(String topic) throws Exception {
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            return admin.listTopics().names().get().contains(topic);
        }
    }

    // ---- proyección ----

    private String projectedStatus(UUID company) {
        return jdbc.sql("SELECT status FROM company_projection WHERE company_id = :id").param("id", company).query(String.class).optional().orElse(null);
    }

    private void awaitProjection(UUID company, String status) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(projectedStatus(company)).isEqualTo(status));
    }

    /** Publica un evento posterior y espera a que se aplique: todo lo publicado antes en la misma partición ya fue consumido. */
    private void awaitConsumedUpToHere() {
        UUID marker = UUID.randomUUID();
        send(COMPANY_TOPIC, marker.toString(), registered(marker, "Marcador", "ACTIVE").json());
        awaitProjection(marker, "ACTIVE");
    }

    private Caller operatorOf(UUID company) {
        return new Caller(UUID.randomUUID(), company, "OPERATOR");
    }

    private Caller adminOf(UUID company) {
        return new Caller(UUID.randomUUID(), company, "COMPANY_ADMIN");
    }

    private long processed(UUID eventId) {
        return count("SELECT count(*) FROM processed_events WHERE event_id = ?", eventId);
    }

    // ---- empresas ----

    @Test
    @Order(1)
    void aCompanyRegisteredBeforeTheServiceFirstStartedIsKnownAndCanOperate() throws Exception {
        awaitProjection(PRE_EXISTING_COMPANY, "ACTIVE");

        JsonNode batch = registerBatchAs(operatorOf(PRE_EXISTING_COMPANY), 10);

        assertThat(batch.path("companyId").asString()).isEqualTo(PRE_EXISTING_COMPANY.toString());
        assertThat(jdbc.sql("SELECT business_name FROM company_projection WHERE company_id = :id").param("id", PRE_EXISTING_COMPANY)
                .query(String.class).single()).isEqualTo("Hilos del Norte SAC");
    }

    @Test
    void anInactivatedCompanyIsBlockedByRf003InEveryOperationThatStartsWorkOrBringsMaterialIn() throws Exception {
        UUID company = UUID.randomUUID();
        UUID supplier = UUID.randomUUID();
        send(COMPANY_TOPIC, company.toString(), registered(company, "Tejidos SAC", "ACTIVE").json());
        send(COMPANY_TOPIC, supplier.toString(), registered(supplier, "Hilandería SAC", "ACTIVE").json());
        awaitProjection(company, "ACTIVE");
        awaitProjection(supplier, "ACTIVE");
        Caller operator = operatorOf(company);
        Caller admin = adminOf(company);
        JsonNode toSplit = registerBatchAs(operator, 10);
        JsonNode toTransform = registerBatchAs(operator, 10);
        JsonNode toTransfer = registerBatchAs(operator, 10);
        UUID machine = idOf(read(postAs(admin, "/api/machines", obj("internalCode", "MQ-1", "name", "Cardadora", "type", "Cardado"))
                .andExpect(status().isCreated())));
        JsonNode incoming = registerBatchAs(operatorOf(supplier), 5);
        UUID transfer = idOf(read(postAs(operatorOf(supplier), "/api/transfers", obj("targetCompanyId", company,
                "batchIds", List.of(idOf(incoming)))).andExpect(status().isCreated())));

        send(COMPANY_TOPIC, company.toString(), statusChanged(company, "INACTIVE").json());
        awaitProjection(company, "INACTIVE");

        assertThat(code(postAs(operator, "/api/batches", batchBody(10)).andExpect(status().isConflict()))).isEqualTo("COMPANY_INACTIVE");
        assertThat(code(postAs(operator, "/api/batches/" + idOf(toSplit) + "/split", obj("children", List.of(obj("quantityKg", 4),
                obj("quantityKg", 6)))).andExpect(status().isConflict()))).isEqualTo("COMPANY_INACTIVE");
        assertThat(code(postAs(operator, "/api/transformations", obj("type", "SPINNING", "machineId", machine,
                "batchIds", List.of(idOf(toTransform)))).andExpect(status().isConflict()))).isEqualTo("COMPANY_INACTIVE");
        assertThat(code(postAs(operator, "/api/transfers", obj("targetCompanyId", supplier, "batchIds", List.of(idOf(toTransfer))))
                .andExpect(status().isConflict()))).isEqualTo("COMPANY_INACTIVE");
        assertThat(code(postAs(admin, "/api/transfers/" + transfer + "/accept").andExpect(status().isConflict()))).isEqualTo("COMPANY_INACTIVE");

        send(COMPANY_TOPIC, company.toString(), statusChanged(company, "ACTIVE").json());
        awaitProjection(company, "ACTIVE");
        postAs(operator, "/api/batches", batchBody(10)).andExpect(status().isCreated());
    }

    @Test
    void theSameEventTwiceIsNotAppliedTwice() {
        UUID company = UUID.randomUUID();
        Sent registered = registered(company, "Tejidos SAC", "ACTIVE");
        Sent inactivated = statusChanged(company, "INACTIVE");
        Sent reactivated = statusChanged(company, "ACTIVE");
        send(COMPANY_TOPIC, company.toString(), registered.json());
        send(COMPANY_TOPIC, company.toString(), inactivated.json());
        send(COMPANY_TOPIC, company.toString(), reactivated.json());
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(processed(reactivated.eventId())).isEqualTo(1));
        assertThat(projectedStatus(company)).isEqualTo("ACTIVE");
        Instant updatedAt = jdbc.sql("SELECT updated_at FROM company_projection WHERE company_id = :id").param("id", company)
                .query(java.time.OffsetDateTime.class).single().toInstant();

        // Kafka entrega el evento viejo otra vez (at-least-once), después del más nuevo: si se aplicara, la dejaría inactiva
        send(COMPANY_TOPIC, company.toString(), inactivated.json());
        send(COMPANY_TOPIC, company.toString(), inactivated.json());
        awaitConsumedUpToHere();

        assertThat(projectedStatus(company)).as("el evento repetido no se vuelve a aplicar").isEqualTo("ACTIVE");
        assertThat(jdbc.sql("SELECT updated_at FROM company_projection WHERE company_id = :id").param("id", company)
                .query(java.time.OffsetDateTime.class).single().toInstant()).isEqualTo(updatedAt);
        assertThat(processed(inactivated.eventId())).isEqualTo(1);
        assertThat(deadLetters(COMPANY_TOPIC, company.toString())).isEmpty();
    }

    @Test
    void eventsThatAreNotProductionsAreIgnoredWithoutErrorWithoutTheDeadLetterTopicAndWithoutATrace() {
        UUID company = UUID.randomUUID();
        Sent privacy = envelope("CompanyPrivacyUpdated", company.toString(), Map.of("companyId", company, "fields", Map.of("SUPPLIER", "SHARED")));
        Sent unknown = envelope("AlgoQueNadieConoce", company.toString(), Map.of("x", 1));
        send(COMPANY_TOPIC, company.toString(), privacy.json());
        send(COMPANY_TOPIC, company.toString(), unknown.json());
        awaitConsumedUpToHere();

        assertThat(processed(privacy.eventId())).isZero();
        assertThat(processed(unknown.eventId())).isZero();
        assertThat(projectedStatus(company)).isNull();
        assertThat(deadLetters(COMPANY_TOPIC, company.toString())).isEmpty();
    }

    @Test
    void aStatusChangeForACompanyTheProjectionDoesNotKnowCreatesItWithThatStateAndALaterRegistrationDoesNotOverrideIt(CapturedOutput output) {
        UUID company = UUID.randomUUID();
        send(COMPANY_TOPIC, company.toString(), statusChanged(company, "INACTIVE").json());
        awaitProjection(company, "INACTIVE");
        assertThat(jdbc.sql("SELECT business_name FROM company_projection WHERE company_id = :id").param("id", company)
                .query(String.class).optional()).isEmpty();
        assertThat(output.getAll()).contains("que la proyección no conocía").contains(company.toString());

        send(COMPANY_TOPIC, company.toString(), registered(company, "Hilos SAC", "ACTIVE").json());
        awaitConsumedUpToHere();

        assertThat(projectedStatus(company)).as("el alta tardía completa el nombre pero no pisa el estado").isEqualTo("INACTIVE");
        assertThat(jdbc.sql("SELECT business_name FROM company_projection WHERE company_id = :id").param("id", company)
                .query(String.class).single()).isEqualTo("Hilos SAC");
    }

    @Test
    void unreadableMessagesGoStraightToTheDeadLetterTopicAndTheConsumerKeepsGoing() {
        String garbage = "garbage-" + UUID.randomUUID();
        UUID badStatusCompany = UUID.randomUUID();
        send(COMPANY_TOPIC, garbage, "esto no es un sobre");
        send(COMPANY_TOPIC, badStatusCompany.toString(), statusChanged(badStatusCompany, "SUSPENDED").json());
        awaitConsumedUpToHere();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(deadLetters(COMPANY_TOPIC, garbage)).hasSize(1);
            assertThat(deadLetters(COMPANY_TOPIC, badStatusCompany.toString())).hasSize(1);
        });
        assertThat(projectedStatus(badStatusCompany)).isNull();
    }

    // ---- quality.control ----

    @Test
    @Order(2)
    void productionStartsWithoutTheQualityTopicNeverCreatesItAndConsumesItOnceItAppears(CapturedOutput output) throws Exception {
        assertThat(topicExists(QUALITY_TOPIC)).as("el tópico de Quality no existe: el servicio arrancó igual").isFalse();
        assertThat(topicExists(QUALITY_TOPIC + ".DLT")).as("sí declara la DLT de lo que consume").isTrue();
        Thread.sleep(6_000);   // varias renovaciones de metadata con el tópico ausente
        assertThat(topicExists(QUALITY_TOPIC)).as("Production no crea tópicos de otros servicios").isFalse();
        assertThat(output.getAll().lines().filter(line -> line.contains("UNKNOWN_TOPIC_OR_PARTITION")).count())
                .as("sin suscribirse a un tópico ausente no hay avisos UNKNOWN_TOPIC_OR_PARTITION").isZero();

        UUID company = UUID.randomUUID();
        send(COMPANY_TOPIC, company.toString(), registered(company, "Tejidos SAC", "ACTIVE").json());
        awaitProjection(company, "ACTIVE");
        JsonNode batch = registerBatchAs(operatorOf(company), 10);
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(QUALITY_TOPIC, 1, (short) 1))).all().get();
        }
        long created = System.nanoTime();
        send(QUALITY_TOPIC, idOf(batch).toString(), qualityStatusChanged(idOf(batch), company, "NOT_REVIEWED", "CONFORMING").json());

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(qualityStatusOf(idOf(batch))).isEqualTo("CONFORMING"));
        assertThat(Duration.ofNanos(System.nanoTime() - created)).as("lo consume poco después de aparecer").isLessThan(Duration.ofSeconds(15));
    }

    private String qualityStatusOf(UUID batchId) {
        return jdbc.sql("SELECT quality_status FROM batches WHERE id = :id").param("id", batchId).query(String.class).single();
    }

    private void ensureQualityTopic() throws Exception {
        if (!topicExists(QUALITY_TOPIC)) {
            try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
                admin.createTopics(List.of(new NewTopic(QUALITY_TOPIC, 1, (short) 1))).all().get();
            }
        }
    }

    private Caller activeCompanyOperator() {
        UUID company = UUID.randomUUID();
        send(COMPANY_TOPIC, company.toString(), registered(company, "Tejidos SAC", "ACTIVE").json());
        awaitProjection(company, "ACTIVE");
        return operatorOf(company);
    }

    @Test
    void aQualityStatusChangeOnlyUpdatesTheDisplayedCopyAndPublishesNothing() throws Exception {
        ensureQualityTopic();
        Caller operator = activeCompanyOperator();
        JsonNode batch = registerBatchAs(operator, 10);
        jdbc.sql("DELETE FROM outbox").update();

        send(QUALITY_TOPIC, idOf(batch).toString(), qualityStatusChanged(idOf(batch), operator.companyId(), "NOT_REVIEWED", "FAILED").json());
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(qualityStatusOf(idOf(batch))).isEqualTo("FAILED"));
        awaitConsumedUpToHere();

        assertThat(count("SELECT count(*) FROM outbox")).as("no se publica BatchPhaseChanged ni nada").isZero();
        assertThat(jdbc.sql("SELECT current_stage, quality_hold FROM batches WHERE id = :id").param("id", idOf(batch))
                .query((rs, n) -> rs.getString(1) + "/" + rs.getBoolean(2)).single()).as("fase y retención no cambian").isEqualTo("AVAILABLE/false");
    }

    @Test
    void aBatchThatChangedCompanyIsStillUpdatedButTheMismatchIsLogged(CapturedOutput output) throws Exception {
        ensureQualityTopic();
        Caller operator = activeCompanyOperator();
        JsonNode batch = registerBatchAs(operator, 10);
        UUID formerOwner = UUID.randomUUID();

        send(QUALITY_TOPIC, idOf(batch).toString(), qualityStatusChanged(idOf(batch), formerOwner, "NOT_REVIEWED", "CONFORMING").json());

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(qualityStatusOf(idOf(batch))).isEqualTo("CONFORMING"));
        assertThat(output.getAll()).contains("el lote cambió de empresa").contains(formerOwner.toString());
        assertThat(deadLetters(QUALITY_TOPIC, idOf(batch).toString())).isEmpty();
    }

    @Test
    void aBatchThatDoesNotExistGoesStraightToTheDeadLetterTopicWithoutRetriesAndLogsAnError(CapturedOutput output) throws Exception {
        ensureQualityTopic();
        UUID missing = UUID.randomUUID();
        Mockito.clearInvocations(qualityStatus);

        send(QUALITY_TOPIC, missing.toString(), qualityStatusChanged(missing, UUID.randomUUID(), "NOT_REVIEWED", "FAILED").json());

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(deadLetters(QUALITY_TOPIC, missing.toString())).hasSize(1));
        Thread.sleep(2_000);   // más que toda la espera de los reintentos (0,2 + 0,3 + 0,5 s)
        verify(qualityStatus, times(1)).apply(eq(missing), any(), any(), any());
        assertThat(output.getAll()).contains("referencia un lote que no existe");
        assertThat(count("SELECT count(*) FROM processed_events WHERE event_type = 'QualityStatusChanged' AND event_id = ?",
                UUID.fromString(MAPPER.readTree(deadLetters(QUALITY_TOPIC, missing.toString()).get(0).value()).path("eventId").asString())))
                .as("el evento fallido no queda como procesado").isZero();
    }

    @Test
    void aVersionConflictWithAUserOperationIsRetriedWithBackoffInsteadOfGoingToTheDeadLetterTopic() throws Exception {
        ensureQualityTopic();
        Caller operator = activeCompanyOperator();
        JsonNode batch = registerBatchAs(operator, 10);
        UUID id = idOf(batch);
        AtomicInteger saves = new AtomicInteger();
        // Entre la lectura y el guardado del consumidor, "otra operación" modifica el lote y confirma (otra conexión): solo la primera vez
        doAnswer(invocation -> {
            if (saves.getAndIncrement() == 0) {
                CompletableFuture.runAsync(() -> jdbc.sql("UPDATE batches SET version = version + 1 WHERE id = :id").param("id", id).update()).join();
            }
            return invocation.callRealMethod();
        }).when(batchRepository).save(any(Batch.class));

        Sent event = qualityStatusChanged(id, operator.companyId(), "NOT_REVIEWED", "PENDING_REEVALUATION");
        send(QUALITY_TOPIC, id.toString(), event.json());

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(qualityStatusOf(id)).isEqualTo("PENDING_REEVALUATION"));
        assertThat(saves.get()).as("el primer intento chocó con la versión; el segundo lo guardó").isEqualTo(2);
        assertThat(processed(event.eventId())).as("procesado una sola vez").isEqualTo(1);
        Thread.sleep(1_000);
        assertThat(deadLetters(QUALITY_TOPIC, id.toString())).as("no fue a la DLT").isEmpty();
    }

    @Test
    void aVersionConflictThatNeverGoesAwayEndsInTheDeadLetterTopicAfterTheRetriesAreExhausted() throws Exception {
        ensureQualityTopic();
        Caller operator = activeCompanyOperator();
        JsonNode batch = registerBatchAs(operator, 10);
        UUID id = idOf(batch);
        AtomicInteger saves = new AtomicInteger();
        doAnswer(invocation -> {
            saves.incrementAndGet();
            CompletableFuture.runAsync(() -> jdbc.sql("UPDATE batches SET version = version + 1 WHERE id = :id").param("id", id).update()).join();
            return invocation.callRealMethod();
        }).when(batchRepository).save(any(Batch.class));

        send(QUALITY_TOPIC, id.toString(), qualityStatusChanged(id, operator.companyId(), "NOT_REVIEWED", "FAILED").json());

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(deadLetters(QUALITY_TOPIC, id.toString())).hasSize(1));
        assertThat(saves.get()).as("1 intento + 3 reintentos").isEqualTo(4);
        assertThat(qualityStatusOf(id)).isEqualTo("NOT_REVIEWED");
    }
}
