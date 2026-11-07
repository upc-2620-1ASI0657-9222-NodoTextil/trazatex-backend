package com.nodotextil.trazatex.notification.infrastructure.messaging;

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
 * Notification con Kafka real y PostgreSQL real: los tópicos ajenos que aún no existen no se crean ni se consumen hasta que aparecen, cada
 * tópico se lee con su propio grupo (un mensaje que falla no frena a los demás), y lo ilegible va a la {@code .DLT} del tópico.
 */
@SpringBootTest(properties = {
        "trazatex.messaging.enabled=true", "SENDGRID_API_KEY=", "SENDGRID_FROM_EMAIL=", "trazatex.mail.job-delay=PT1H",
        "trazatex.messaging.consumer.topic-check-interval=PT1S",
        "trazatex.messaging.consumer.retry.max-retries=2",
        "trazatex.messaging.consumer.retry.initial-interval=PT0.2S",
        "trazatex.messaging.consumer.retry.max-interval=PT0.5S"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationKafkaIT {
    static final KafkaContainer KAFKA = new TrazatexKafkaContainer();
    static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:16.15").withPassword(UUID.randomUUID().toString());
    static final ObjectMapper MAPPER = JsonMapper.builder().build();
    static final List<String> SOURCES = List.of("orgaccess.user", "orgaccess.invitation", "production.transfer", "traceability.impact");
    static final Map<String, String> GROUPS = Map.of(
            "orgaccess.user", "notification.orgaccess-user", "orgaccess.invitation", "notification.orgaccess-invitation",
            "production.transfer", "notification.production-transfer", "traceability.impact", "notification.traceability-impact");
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
            for (String id : List.of("notification-orgaccess-user", "notification-orgaccess-invitation", "notification-production-transfer", "notification-traceability-impact"))
                assertThat(registry.getListenerContainer(id).isRunning()).as(id).isFalse();
        });
        // lo único que declara son las .DLT de lo que consume
        assertThat(topics()).containsAll(SOURCES.stream().map(topic -> topic + ".DLT").toList());
    }

    @Test @Order(2)
    void eachTopicStartsWhenItAppearsAndIsReadByItsOwnGroup() throws Exception {
        UUID company = UUID.randomUUID(), admin = UUID.randomUUID(), transfer = UUID.randomUUID(), request = UUID.randomUUID(), failure = UUID.randomUUID(), batch = UUID.randomUUID();
        try (AdminClient admins = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admins.createTopics(SOURCES.stream().map(topic -> new NewTopic(topic, 1, (short) 1)).toList()).all().get();
        }
        // el directorio primero: el orden entre tópicos no está garantizado y el correo se decide al crear la notificación
        send("orgaccess.user", admin.toString(), event("UserAccountUpdated", admin, Map.of("userId", admin.toString(), "companyId", company.toString(),
                "role", "COMPANY_ADMIN", "email", "kafka-" + admin + "@correo.controlado", "status", "ACTIVE")));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(jdbc.sql("SELECT count(*) FROM user_projection WHERE user_id=:u").param("u", admin).query(Long.class).single()).isEqualTo(1));

        send("production.transfer", transfer.toString(), event("TransferInitiated", transfer, Map.of("transferId", transfer.toString(),
                "batchIds", List.of(batch.toString()), "fromCompanyId", UUID.randomUUID().toString(), "toCompanyId", company.toString())));
        send("orgaccess.invitation", request.toString(), event("AdminRequestSubmitted", request, Map.of("requestId", request.toString(), "companyId", company.toString())));
        send("traceability.impact", failure.toString(), event("PossibleDerivedFailureDetected", failure, Map.of("failureId", failure.toString(),
                "sourceBatchId", UUID.randomUUID().toString(), "affected", List.of(
                        Map.of("batchId", batch.toString(), "companyId", company.toString(), "commonAncestorBatchId", UUID.randomUUID().toString())))));

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
            assertThat(types(transfer.toString())).containsExactly("TRANSFER_RECEIVED");
            assertThat(types(request.toString())).containsExactly("ADMIN_REQUEST_SUBMITTED");
            assertThat(jdbc.sql("SELECT type FROM notification WHERE data->>'failureId'=:f").param("f", failure.toString()).query(String.class).list())
                    .containsExactly("POTENTIAL_DERIVED_FAILURE");
        });
        // el correo del administrador activo nació con la notificación de la transferencia y con la de la falla
        assertThat(jdbc.sql("SELECT count(*) FROM deferred_email WHERE recipient=:r").param("r", "kafka-" + admin + "@correo.controlado").query(Long.class).single()).isEqualTo(2);

        try (AdminClient admins = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            for (var group : GROUPS.entrySet()) {
                Set<String> read = admins.listConsumerGroupOffsets(group.getValue()).partitionsToOffsetAndMetadata().get().keySet().stream()
                        .map(partition -> partition.topic()).collect(Collectors.toSet());
                assertThat(read).as(group.getValue()).containsExactly(group.getKey());
            }
        }
    }

    @Test @Order(3)
    void anUnreadableMessageGoesToItsDeadLetterTopicWithoutBlockingTheOtherTopics() throws Exception {
        UUID request = UUID.randomUUID();
        send("production.transfer", "basura", "esto no es un evento");
        send("orgaccess.invitation", request.toString(), event("AdminRequestSubmitted", request, Map.of("requestId", request.toString(), "companyId", UUID.randomUUID().toString())));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(types(request.toString())).containsExactly("ADMIN_REQUEST_SUBMITTED"));
        var config = new HashMap<String, Object>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "prueba-dlt-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (var consumer = new KafkaConsumer<String, String>(config)) {
            consumer.subscribe(List.of("production.transfer.DLT"));
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                List<String> values = new ArrayList<>();
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) values.add(record.value());
                assertThat(values).contains("esto no es un evento");
            });
        }
    }

    @Test @Order(4)
    void anEventPublishedTwiceIsAppliedOnce() throws Exception {
        UUID request = UUID.randomUUID(), marker = UUID.randomUUID();
        String json = event("AdminRequestSubmitted", request, Map.of("requestId", request.toString(), "companyId", UUID.randomUUID().toString()));
        send("orgaccess.invitation", request.toString(), json);
        send("orgaccess.invitation", request.toString(), json);
        send("orgaccess.invitation", marker.toString(), event("AdminRequestSubmitted", marker, Map.of("requestId", marker.toString(), "companyId", UUID.randomUUID().toString())));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(types(marker.toString())).hasSize(1));
        assertThat(types(request.toString())).hasSize(1);
    }

    /** US22: aceptar o rechazar avisa a la empresa de origen —y solo a ella—, con códigos distintos, por Kafka real y con el directorio ya conocido. */
    @Test @Order(5)
    void anAcceptedAndARejectedTransferAlertOnlyTheOriginCompanyThroughKafka() throws Exception {
        UUID origin = UUID.randomUUID(), destination = UUID.randomUUID(), originAdmin = UUID.randomUUID(), destinationAdmin = UUID.randomUUID();
        UUID accepted = UUID.randomUUID(), rejected = UUID.randomUUID(), marker = UUID.randomUUID();
        send("orgaccess.user", originAdmin.toString(), event("UserAccountUpdated", originAdmin, Map.of("userId", originAdmin.toString(), "companyId", origin.toString(),
                "role", "COMPANY_ADMIN", "email", "origen-" + originAdmin + "@correo.controlado", "status", "ACTIVE")));
        send("orgaccess.user", destinationAdmin.toString(), event("UserAccountUpdated", destinationAdmin, Map.of("userId", destinationAdmin.toString(), "companyId", destination.toString(),
                "role", "COMPANY_ADMIN", "email", "destino-" + destinationAdmin + "@correo.controlado", "status", "ACTIVE")));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(jdbc.sql("SELECT count(*) FROM user_projection WHERE user_id IN (:a,:b)").param("a", originAdmin).param("b", destinationAdmin).query(Long.class).single()).isEqualTo(2));

        for (var resolution : Map.of("TransferAccepted", accepted, "TransferRejected", rejected).entrySet())
            send("production.transfer", resolution.getValue().toString(), event(resolution.getKey(), resolution.getValue(), Map.of("transferId", resolution.getValue().toString(),
                    "batchIds", List.of(UUID.randomUUID().toString()), "fromCompanyId", origin.toString(), "toCompanyId", destination.toString())));
        send("orgaccess.invitation", marker.toString(), event("AdminRequestSubmitted", marker, Map.of("requestId", marker.toString(), "companyId", UUID.randomUUID().toString())));

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
            assertThat(types(accepted.toString())).containsExactly("TRANSFER_ACCEPTED");
            assertThat(types(rejected.toString())).containsExactly("TRANSFER_REJECTED");
        });
        assertThat(jdbc.sql("SELECT DISTINCT company_id FROM notification WHERE resource_id IN (:a,:r)").param("a", accepted.toString()).param("r", rejected.toString())
                .query(UUID.class).list()).as("solo la empresa de origen").containsExactly(origin);
        assertThat(jdbc.sql("SELECT count(*) FROM deferred_email WHERE recipient=:r").param("r", "origen-" + originAdmin + "@correo.controlado").query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM deferred_email WHERE recipient=:r").param("r", "destino-" + destinationAdmin + "@correo.controlado").query(Long.class).single()).isZero();
    }

    private List<String> types(String resourceId) {
        return jdbc.sql("SELECT type FROM notification WHERE resource_id=:r").param("r", resourceId).query(String.class).list();
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
