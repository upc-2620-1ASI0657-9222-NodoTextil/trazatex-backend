package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.traceability.integration.EventMessages.Message;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * De punta a punta con Kafka real (US36): Organization &amp; Access publica {@code CompanyPrivacyUpdated}, traceability lo consume y lo que ve otra empresa
 * cambia por la API. La empresa B marca un campo {@code SHARED} → la empresa A, que consulta, lo ve (también en los lotes que B registró <b>antes</b>);
 * B lo vuelve a {@code PRIVATE} → A deja de verlo. Una tercera empresa que consulta ve lo mismo que A, y B siempre ve lo suyo.
 */
@SpringBootTest(properties = {"trazatex.messaging.enabled=true", "trazatex.messaging.consumer.topic-check-interval=PT0.5S"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PrivacyOverKafkaIT extends IntegrationTestBase {

    static final KafkaContainer KAFKA = KafkaTestSupport.startKafka();
    static final KafkaProducer<String, String> PRODUCER;
    static final Duration PATIENCE = Duration.ofSeconds(60);

    static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    static final UUID C = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    static final UUID OLD_BATCH = UUID.fromString("00000000-0000-0000-0000-00000000b001");
    static final UUID NEW_BATCH = UUID.fromString("00000000-0000-0000-0000-00000000b002");
    static final UUID OUT_OLD = UUID.fromString("00000000-0000-0000-0000-00000000a0e1");
    static final UUID OUT_NEW = UUID.fromString("00000000-0000-0000-0000-00000000a0e2");

    static {
        KafkaTestSupport.createTopics(KAFKA.getBootstrapServers(), Map.of("production.batch", 1, "production.transformation", 1, "production.transfer", 1, "orgaccess.company", 1,
                "quality.control", 1));
        PRODUCER = KafkaTestSupport.producer(KAFKA.getBootstrapServers());
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("trazatex.messaging.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper json;

    private void publishAndAwait(Message... messages) {
        for (Message message : messages) {
            KafkaTestSupport.send(PRODUCER, message);
        }
        GraphAssertions.awaitProcessed(neo4j, java.util.Arrays.stream(messages).map(Message::eventId).toList(), PATIENCE);
    }

    private JsonNode lineageOf(UUID batch, UUID company) throws Exception {
        String body = mockMvc.perform(get("/api/traceability/batches/" + batch + "/ancestors").header("X-User-Id", "00000000-0000-0000-0000-00000000a0a1")
                .header("X-Company-Id", company.toString()).header("X-User-Role", "OPERATOR")).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private static JsonNode node(JsonNode lineage, UUID id) {
        for (JsonNode node : lineage.get("nodes")) {
            if (node.get("id").asString().equals(id.toString())) {
                return node;
            }
        }
        throw new AssertionError(id + " no está en " + lineage);
    }

    private static Map<String, String> privacy(String field, String visibility) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(field, visibility);
        return map;
    }

    @Test
    void aFieldMarkedSharedIsSeenByTheOtherCompanyAndStopsBeingSeenWhenItGoesBackToPrivate() throws Exception {
        Message[] setup = {EventMessages.companyRegistered(UUID.randomUUID(), A, "Andina SAC"), EventMessages.companyRegistered(UUID.randomUUID(), B, "Bahía SAC"),
                EventMessages.companyRegistered(UUID.randomUUID(), C, "Cumbre SAC"),
                EventMessages.batchRegistered(UUID.randomUUID(), OLD_BATCH, "TT-B001", "QR-B001", B, "Algodón", 100, "Piura", "Proveedor de B", "Fibra limpia", GraphBuilder.T0),
                EventMessages.transformationCompleted(UUID.randomUUID(), UUID.randomUUID(), "DYEING", A, "Teñidora de A", "Teñido", List.of(OLD_BATCH), OUT_OLD, "TT-AOLD",
                        "QR-AOLD", "Tela teñida", 90, false, 5, "Orillo", 5, GraphBuilder.T0, GraphBuilder.T0.plusSeconds(3600))};
        publishAndAwait(setup);

        // 1. B no configuró nada: A ve el lote de B solo con lo siempre compartido
        JsonNode before = lineageOf(OUT_OLD, A);
        assertThat(node(before, OLD_BATCH).get("access").asString()).isEqualTo("SHARED");
        assertThat(node(before, OLD_BATCH).get("traceabilityId").asString()).isEqualTo("TT-B001");
        assertThat(node(before, OLD_BATCH).has("supplierName")).as("sin configuración, privado").isFalse();

        // 2. B marca SUPPLIER como SHARED: A lo ve, también en el lote que B registró ANTES; lo demás sigue oculto
        publishAndAwait(EventMessages.companyPrivacyUpdated(UUID.randomUUID(), B, privacy("SUPPLIER", "SHARED")));
        JsonNode shared = lineageOf(OUT_OLD, A);
        assertThat(node(shared, OLD_BATCH).get("supplierName").asString()).as("el lote antiguo de B ahora muestra el proveedor").isEqualTo("Proveedor de B");
        assertThat(node(shared, OLD_BATCH).has("origin")).as("lo que B no compartió sigue oculto").isFalse();
        assertThat(node(shared, OLD_BATCH).has("quantityKg")).isFalse();
        assertThat(lineageOf(OUT_OLD, C).get("nodes").toString()).as("una tercera empresa ve lo mismo que A").contains("Proveedor de B");

        // y un lote que B registra DESPUÉS, también
        publishAndAwait(EventMessages.batchRegistered(UUID.randomUUID(), NEW_BATCH, "TT-B002", "QR-B002", B, "Algodón", 100, "Piura", "Otro proveedor de B", "Fibra limpia",
                        GraphBuilder.T0.plusSeconds(10)),
                EventMessages.transformationCompleted(UUID.randomUUID(), UUID.randomUUID(), "DYEING", A, "Teñidora de A", "Teñido", List.of(NEW_BATCH), OUT_NEW, "TT-ANEW",
                        "QR-ANEW", "Tela teñida", 90, false, 5, "Orillo", 5, GraphBuilder.T0, GraphBuilder.T0.plusSeconds(3600)));
        assertThat(node(lineageOf(OUT_NEW, A), NEW_BATCH).get("supplierName").asString()).isEqualTo("Otro proveedor de B");

        // 3. B vuelve a marcar SUPPLIER como PRIVATE: A deja de verlo, en los lotes de antes y en los de después
        publishAndAwait(EventMessages.companyPrivacyUpdated(UUID.randomUUID(), B, privacy("SUPPLIER", "PRIVATE")));
        JsonNode hidden = lineageOf(OUT_OLD, A);
        assertThat(node(hidden, OLD_BATCH).has("supplierName")).as("otra vez privado").isFalse();
        assertThat(hidden.toString()).doesNotContain("Proveedor de B");
        assertThat(node(lineageOf(OUT_NEW, A), NEW_BATCH).has("supplierName")).isFalse();
        assertThat(lineageOf(OUT_OLD, C).toString()).doesNotContain("Proveedor de B");

        // B, la dueña, siempre ve lo suyo, comparta o no
        assertThat(node(lineageOf(OUT_OLD, B), OLD_BATCH).get("supplierName").asString()).isEqualTo("Proveedor de B");

        // nada de esto fue a la DLT
        List<ConsumerRecord<String, String>> dlt = KafkaTestSupport.readAll(KAFKA.getBootstrapServers(), "orgaccess.company.DLT", Duration.ofSeconds(1));
        assertThat(dlt).isEmpty();
    }
}
