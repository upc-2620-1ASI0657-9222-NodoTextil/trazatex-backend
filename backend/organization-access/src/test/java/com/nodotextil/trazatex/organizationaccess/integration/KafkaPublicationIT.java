package com.nodotextil.trazatex.organizationaccess.integration;

import com.nodotextil.trazatex.organizationaccess.application.port.in.BootstrapOwnerUseCase;
import com.nodotextil.trazatex.organizationaccess.application.port.out.OrganizationEventPublisherPort;
import com.nodotextil.trazatex.organizationaccess.domain.event.CompanyStatusChanged;
import com.nodotextil.trazatex.organizationaccess.domain.model.CompanyStatus;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import com.nodotextil.trazatex.sharedtesting.TrazatexKafkaContainer;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-004 de extremo a extremo con Kafka real: los eventos salen del outbox hacia Kafka <b>después</b> del commit, nunca con
 * rollback, con la clave y el sobre del catálogo, en orden por agregado.
 */
@SpringBootTest(properties = "trazatex.messaging.enabled=true")
class KafkaPublicationIT extends IntegrationTestBase {

    static final KafkaContainer KAFKA = new TrazatexKafkaContainer();

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("trazatex.messaging.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("trazatex.messaging.relay-delay", () -> "PT0.2S");
    }

    private static final List<String> TOPICS = List.of("orgaccess.company", "orgaccess.invitation", "orgaccess.user");

    @Autowired
    OrganizationEventPublisherPort events;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    BootstrapOwnerUseCase bootstrap;

    private Map<TopicPartition, Long> startOffsets;

    @BeforeEach
    void rememberWhereKafkaWas() {
        startOffsets = endOffsets();
    }

    // ---- utilidades de Kafka ----

    private KafkaConsumer<String, String> consumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return new KafkaConsumer<>(props);
    }

    private Map<TopicPartition, Long> endOffsets() {
        try (KafkaConsumer<String, String> consumer = consumer()) {
            List<TopicPartition> partitions = TOPICS.stream()
                    .flatMap(topic -> consumer.partitionsFor(topic).stream().map(info -> new TopicPartition(topic, info.partition())))
                    .toList();
            return new HashMap<>(consumer.endOffsets(partitions));
        }
    }

    /** Todo lo publicado en el tópico desde que empezó la prueba. */
    private List<ConsumerRecord<String, String>> published(String topic) {
        try (KafkaConsumer<String, String> consumer = consumer()) {
            List<TopicPartition> partitions = startOffsets.keySet().stream().filter(tp -> tp.topic().equals(topic)).toList();
            consumer.assign(partitions);
            partitions.forEach(tp -> consumer.seek(tp, startOffsets.get(tp)));
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);
            List<ConsumerRecord<String, String>> records = new java.util.ArrayList<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (System.nanoTime() < deadline && partitions.stream().anyMatch(tp -> consumer.position(tp) < end.get(tp))) {
                consumer.poll(Duration.ofMillis(300)).forEach(records::add);
            }
            return records;
        }
    }

    private List<ConsumerRecord<String, String>> publishedWithKey(String topic, String key) {
        return published(topic).stream().filter(record -> key.equals(record.key())).toList();
    }

    private JsonNode parse(ConsumerRecord<String, String> record) throws Exception {
        return json.readTree(record.value());
    }

    // ---- pruebas ----

    @Test
    void theServiceDeclaresItsTopics() throws Exception {
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(props)) {
            var descriptions = admin.describeTopics(TOPICS).allTopicNames().get();
            assertThat(descriptions).containsKeys(TOPICS.toArray(String[]::new));
            descriptions.values().forEach(topic -> assertThat(topic.partitions()).hasSize(1));
        }
    }

    @Test
    void userAccountUpdatedCarriesTheCompleteStateKeyedByUserIdSoTheTopicCanBeCompacted() throws Exception {
        UUID company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        UUID admin = insertUser(company, "Ana", "ana@hilos.pe", "COMPANY_ADMIN", "ACTIVE");
        UUID operator = insertUser(company, "Luis", "luis@hilos.pe", "OPERATOR", "ACTIVE");
        for (String next : new String[]{"INACTIVE", "ACTIVE", "INACTIVE"}) {
            perform(body(asAdmin(PATCH("/api/users/" + operator + "/status"), admin, company), "{\"status\":\"" + next + "\"}")
                    .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
        }

        await().atMost(Duration.ofSeconds(20)).until(() -> publishedWithKey("orgaccess.user", operator.toString()).size() == 3);

        // El Gateway se queda con el último mensaje de cada clave: debe ser el estado completo, no un delta
        JsonNode last = parse(publishedWithKey("orgaccess.user", operator.toString()).get(2));
        assertThat(last.get("type").asString()).isEqualTo("UserAccountUpdated");
        assertThat(last.get("aggregateId").asString()).isEqualTo(operator.toString());
        JsonNode payload = last.get("payload");
        assertThat(payload.get("userId").asString()).isEqualTo(operator.toString());
        assertThat(payload.get("status").asString()).isEqualTo("INACTIVE");
        assertThat(payload.get("email").asString()).isEqualTo("luis@hilos.pe");
        assertThat(payload.get("role").asString()).isEqualTo("OPERATOR");
        assertThat(payload.get("companyId").asString()).isEqualTo(company.toString());
        assertThat(payload.has("fullName")).isTrue();
        assertThat(publishedWithKey("orgaccess.user", operator.toString()).stream().map(r -> {
            try {
                return parse(r).get("payload").get("status").asString();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).toList()).containsExactly("INACTIVE", "ACTIVE", "INACTIVE");
        // y todo lo que hay en el tópico lleva como clave un userId (nunca otra cosa que compactaría eventos distintos)
        assertThat(published("orgaccess.user")).allSatisfy(record -> {
            assertThat(UUID.fromString(record.key())).isNotNull();
            assertThat(parse(record).get("type").asString()).isEqualTo("UserAccountUpdated");
        });
    }

    @Test
    void anInvitationEventReachesKafkaAfterTheCommitWithTheKeyAndTheEnvelope() throws Exception {
        UUID company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        UUID admin = insertUser(company, "Ana", "ana@hilos.pe", "COMPANY_ADMIN", "ACTIVE");

        String invitationId = json.readTree(perform(body(asAdmin(POST("/api/invitations/operators"), admin, company),
                "{\"firstName\":\"Luis\",\"lastName\":\"Rojas\",\"email\":\"luis@hilos.pe\",\"position\":\"Tejedor\"}")
                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();

        await().atMost(Duration.ofSeconds(20)).until(() -> !publishedWithKey("orgaccess.invitation", invitationId).isEmpty());
        JsonNode envelope = parse(publishedWithKey("orgaccess.invitation", invitationId).get(0));

        assertThat(envelope.get("eventId").asString()).isNotBlank();
        assertThat(envelope.get("type").asString()).isEqualTo("InvitationCreated");
        assertThat(envelope.get("aggregateId").asString()).isEqualTo(invitationId);
        assertThat(envelope.get("version").asInt()).isEqualTo(1);
        assertThat(envelope.get("occurredAt").asString()).isNotBlank();
        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("invitationId").asString()).isEqualTo(invitationId);
        assertThat(payload.get("email").asString()).isEqualTo("luis@hilos.pe");
        assertThat(payload.get("fullName").asString()).isEqualTo("Luis Rojas");
        assertThat(payload.get("role").asString()).isEqualTo("OPERATOR");
        assertThat(payload.get("companyId").asString()).isEqualTo(company.toString());
        assertThat(payload.get("acceptUrl").asString()).startsWith("http://localhost:5173/invitations/");
        assertThat(payload.get("expiresAt").asString()).isNotBlank();

        await().atMost(Duration.ofSeconds(10)).until(() -> count("SELECT count(*) FROM outbox WHERE status = 'PUBLISHED' AND published_at IS NOT NULL") == 1);
        assertThat(count("SELECT count(*) FROM outbox WHERE status = 'PENDING'")).isZero();
    }

    @Test
    void aRejectedRequestPublishesNothing() throws Exception {
        UUID company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        UUID admin = insertUser(company, "Ana", "ana@hilos.pe", "COMPANY_ADMIN", "ACTIVE");
        insertInvitation(company, "uno@hilos.pe", "OPERATOR", "PENDING", admin, START);
        insertInvitation(company, "dos@hilos.pe", "OPERATOR", "PENDING", admin, START);   // sin cupo

        perform(body(asAdmin(POST("/api/invitations/operators"), admin, company),
                "{\"firstName\":\"Luis\",\"lastName\":\"Rojas\",\"email\":\"luis@hilos.pe\",\"position\":\"Tejedor\"}"))
                .andExpect(status().isConflict());
        Thread.sleep(1500);   // varias pasadas del relay

        assertThat(published("orgaccess.invitation")).isEmpty();
        assertThat(count("SELECT count(*) FROM outbox")).isZero();
    }

    @Test
    void anEventRecordedInsideATransactionIsNotPublishedUntilItCommits() throws Exception {
        UUID companyId = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            events.publish(new CompanyStatusChanged(companyId, CompanyStatus.INACTIVE));
            sleep(1500);   // el relay pasa varias veces mientras la transacción sigue abierta
            assertThat(countFromAnotherConnection("SELECT count(*) FROM outbox")).as("la fila no es visible fuera de la transacción").isZero();
            assertThat(publishedWithKey("orgaccess.company", companyId.toString())).isEmpty();
        });

        await().atMost(Duration.ofSeconds(20)).until(() -> !publishedWithKey("orgaccess.company", companyId.toString()).isEmpty());
        assertThat(parse(publishedWithKey("orgaccess.company", companyId.toString()).get(0)).get("type").asString()).isEqualTo("CompanyStatusChanged");
    }

    @Test
    void anEventRecordedInATransactionThatRollsBackIsNeverPublished() throws Exception {
        UUID companyId = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            events.publish(new CompanyStatusChanged(companyId, CompanyStatus.INACTIVE));
            status.setRollbackOnly();
        });
        Thread.sleep(1500);

        assertThat(count("SELECT count(*) FROM outbox")).isZero();
        assertThat(publishedWithKey("orgaccess.company", companyId.toString())).isEmpty();
    }

    @Test
    void theEventsOfOneAggregateArePublishedInOrderWithTheSameKey() throws Exception {
        UUID company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        for (String next : new String[]{"INACTIVE", "ACTIVE", "INACTIVE", "ACTIVE"}) {
            perform(body(asOwner(PATCH("/api/companies/" + company + "/status")), "{\"status\":\"" + next + "\"}")).andExpect(status().isOk());
        }

        await().atMost(Duration.ofSeconds(20)).until(() -> publishedWithKey("orgaccess.company", company.toString()).size() == 4);

        List<String> statuses = publishedWithKey("orgaccess.company", company.toString()).stream()
                .map(record -> {
                    try {
                        return parse(record).get("payload").get("status").asString();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .collect(Collectors.toList());
        assertThat(statuses).containsExactly("INACTIVE", "ACTIVE", "INACTIVE", "ACTIVE");
    }

    @Test
    void theOwnerBootstrapPublishesTheInvitationWithTheAcceptLink() throws Exception {
        jdbc.sql("TRUNCATE outbox, invitations, users, license, companies CASCADE").update();

        bootstrap.run();

        await().atMost(Duration.ofSeconds(20)).until(() -> published("orgaccess.invitation").size() == 1);
        JsonNode payload = parse(published("orgaccess.invitation").get(0)).get("payload");
        assertThat(payload.get("role").asString()).isEqualTo("LICENSE_OWNER");
        assertThat(payload.get("email").asString()).isEqualTo(OWNER_EMAIL);
        assertThat(payload.get("fullName").asString()).isEqualTo("Rosa Díaz de la Cruz");
        assertThat(payload.get("companyId").isNull()).isTrue();
        String token = tokenOfEmail(OWNER_EMAIL);
        assertThat(payload.get("acceptUrl").asString()).isEqualTo("http://localhost:5173/invitations/" + token);
    }

    /** JdbcClient comparte la transacción del hilo actual: otro hilo = otra conexión, que solo ve lo confirmado. */
    private long countFromAnotherConnection(String sql) {
        try {
            return java.util.concurrent.CompletableFuture.supplyAsync(() -> count(sql)).get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
