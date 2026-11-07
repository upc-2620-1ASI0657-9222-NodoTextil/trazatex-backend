package com.nodotextil.trazatex.production.integration;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import com.nodotextil.trazatex.sharedtesting.TrazatexKafkaContainer;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-004 de extremo a extremo con Kafka real: los eventos de Production salen del outbox hacia Kafka después del commit,
 * nunca con rollback, con la clave y el sobre del catálogo y en orden por lote (incluido el padre de una división, cuyo
 * {@code BatchSplit} comparte clave con su {@code BatchRegistered} y su {@code BatchPhaseChanged}).
 */
@SpringBootTest(properties = {"trazatex.messaging.enabled=true", "trazatex.cloudinary.url=", "trazatex.evidence.upload.enabled=false"})
class KafkaPublicationIT extends ApiTestBase {

    static final KafkaContainer KAFKA = new TrazatexKafkaContainer();

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("trazatex.messaging.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("trazatex.messaging.relay-delay", () -> "PT0.2S");
    }

    private static final List<String> TOPICS = List.of("production.batch", "production.transformation", "production.transfer");

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
            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (System.nanoTime() < deadline && partitions.stream().anyMatch(tp -> consumer.position(tp) < end.get(tp))) {
                consumer.poll(Duration.ofMillis(300)).forEach(records::add);
            }
            return records;
        }
    }

    private List<JsonNode> publishedWithKey(String topic, String key) {
        return published(topic).stream().filter(record -> key.equals(record.key())).map(this::parse).toList();
    }

    private JsonNode parse(ConsumerRecord<String, String> record) {
        try {
            return json.readTree(record.value());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> types(List<JsonNode> envelopes) {
        return envelopes.stream().map(e -> e.path("type").asString()).toList();
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
            // Traceability reconstruye la genealogía desde el inicio: sin borrado por tiempo ni compactación
            for (String topic : TOPICS) {
                var config = admin.describeConfigs(List.of(new org.apache.kafka.common.config.ConfigResource(
                        org.apache.kafka.common.config.ConfigResource.Type.TOPIC, topic))).all().get().values().iterator().next();
                assertThat(config.get("retention.ms").value()).as("retención de %s", topic).isEqualTo("-1");
                assertThat(config.get("cleanup.policy").value()).as("%s sin compactar", topic).isEqualTo("delete");
            }
        }
    }

    @Test
    void theEventsOfASplitParentShareItsKeyAndKeepTheirOrder() throws Exception {
        JsonNode parent = registerBatch(10);
        String parentId = idOf(parent).toString();
        postAs(OPERATOR_OF_A, "/api/batches/" + parentId + "/split", obj("children", List.of(obj("quantityKg", 4), obj("quantityKg", 6))))
                .andExpect(status().isCreated());

        await().atMost(Duration.ofSeconds(20)).until(() -> publishedWithKey("production.batch", parentId).size() == 3);

        List<JsonNode> events = publishedWithKey("production.batch", parentId);
        assertThat(types(events)).containsExactly("BatchRegistered", "BatchSplit", "BatchPhaseChanged");
        assertThat(events.get(2).path("payload").path("previousPhase").asString()).isEqualTo("AVAILABLE");
        assertThat(events.get(2).path("payload").path("newPhase").asString()).isEqualTo("SPLIT");
        assertThat(events.get(1).path("payload").path("parentBatchId").asString()).isEqualTo(parentId);
        // los hijos no llevan BatchRegistered ni BatchPhaseChanged: nacen en BatchSplit
        for (JsonNode child : events.get(1).path("payload").path("children")) {
            assertThat(publishedWithKey("production.batch", child.path("batchId").asString())).isEmpty();
        }
    }

    @Test
    void aBatchWalksThroughTransformationAndTransferWithItsEventsInOrderAndTheEnvelopeOfTheCatalog() throws Exception {
        JsonNode input = registerBatch(100);
        String inputId = idOf(input).toString();
        UUID machine = idOf(registerMachine("MQ-K1"));
        UUID transformation = idOf(startTransformation("DYEING", machine, idOf(input)));
        JsonNode completed = read(postAs(OPERATOR_OF_A, "/api/transformations/" + transformation + "/complete",
                obj("outputs", List.of(obj("materialType", "Tela", "quantityKg", 90)), "wasteKg", 5, "wasteReason", "Orillo"))
                .andExpect(status().isOk()));
        String outputId = completed.path("outputs").get(0).path("id").asString();
        UUID transfer = idOf(read(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B,
                "batchIds", List.of(UUID.fromString(outputId)))).andExpect(status().isCreated())));
        postAs(ADMIN_OF_B, "/api/transfers/" + transfer + "/accept").andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(20)).until(() -> publishedWithKey("production.batch", outputId).size() == 2
                && publishedWithKey("production.transfer", transfer.toString()).size() == 2
                && publishedWithKey("production.transformation", transformation.toString()).size() == 1);

        assertThat(types(publishedWithKey("production.batch", inputId))).containsExactly("BatchRegistered", "BatchPhaseChanged", "BatchPhaseChanged");
        assertThat(publishedWithKey("production.batch", inputId).stream().skip(1).map(e -> e.path("payload").path("newPhase").asString()))
                .containsExactly("IN_TRANSFORMATION", "PROCESSED");
        List<JsonNode> output = publishedWithKey("production.batch", outputId);
        assertThat(types(output)).containsExactly("BatchPhaseChanged", "BatchPhaseChanged");
        assertThat(output.get(0).path("payload").path("newPhase").asString()).isEqualTo("IN_TRANSFER");
        assertThat(output.get(1).path("payload").path("newPhase").asString()).isEqualTo("AVAILABLE");
        assertThat(output.get(1).path("payload").path("companyId").asString()).isEqualTo(COMPANY_B.toString());
        assertThat(types(publishedWithKey("production.transfer", transfer.toString()))).containsExactly("TransferInitiated", "TransferAccepted");

        JsonNode envelope = publishedWithKey("production.transformation", transformation.toString()).get(0);
        assertThat(envelope.propertyNames()).containsExactlyInAnyOrder("eventId", "type", "aggregateId", "version", "occurredAt", "payload");
        assertThat(envelope.path("type").asString()).isEqualTo("TransformationCompleted");
        assertThat(envelope.path("version").asInt()).isEqualTo(1);
        assertThat(envelope.path("payload").path("outputs").get(0).path("batchId").asString()).isEqualTo(outputId);

        await().atMost(Duration.ofSeconds(10)).until(() -> count("SELECT count(*) FROM outbox WHERE status = 'PENDING'") == 0);
        assertThat(count("SELECT count(*) FROM outbox WHERE published_at IS NULL")).isZero();
    }

    @Test
    void aRejectedOperationPublishesNothing() throws Exception {
        JsonNode held = registerBatch(10);
        jdbc.sql("UPDATE batches SET quality_hold = TRUE WHERE id = :id").param("id", idOf(held)).update();
        await().atMost(Duration.ofSeconds(20)).until(() -> count("SELECT count(*) FROM outbox WHERE status = 'PENDING'") == 0);
        Map<TopicPartition, Long> before = endOffsets();

        postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(held))))
                .andExpect(status().isConflict());
        Thread.sleep(1500);   // varias pasadas del relay

        assertThat(endOffsets()).isEqualTo(before);
        assertThat(count("SELECT count(*) FROM outbox WHERE event_type <> 'BatchRegistered'")).isZero();
    }
}
