package com.nodotextil.trazatex.traceability.acceptance;

import com.nodotextil.trazatex.traceability.integration.EventMessages.Message;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.springframework.data.neo4j.core.Neo4jClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Lo que los escenarios hacen con el mundo exterior: publicar como los demás servicios y mirar el grafo. */
public class Platform {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final Neo4jClient neo4j;

    Platform(Neo4jClient neo4j) {
        this.neo4j = neo4j;
    }

    /** Publica los mensajes en sus tópicos, en este orden, y espera a que traceability los haya procesado todos. */
    public void publishAndAwait(List<Message> messages) {
        for (Message message : messages) {
            try {
                AcceptanceKafka.PRODUCER.send(new ProducerRecord<>(message.topic(), message.key(), message.json())).get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo publicar en " + message.topic(), e);
            }
            awaitProcessed(List.of(message.eventId()));
        }
    }

    private void awaitProcessed(Collection<UUID> eventIds) {
        Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(50)).alias("evento procesado por traceability").untilAsserted(() ->
                assertThat((Long) neo4j.query("MATCH (p:ProcessedEvent) WHERE p.eventId IN $ids RETURN count(p) AS n")
                        .bind(eventIds.stream().map(Object::toString).toList()).to("ids").fetch().one().orElseThrow().get("n")).isEqualTo((long) eventIds.size()));
    }

    /**
     * Lo que traceability publicó en {@code traceability.impact} para esta falla (la clave del mensaje es el {@code failureId}): espera a que el relay del outbox lo
     * entregue y devuelve el sobre completo. Falla si en un minuto no aparece.
     */
    public JsonNode awaitImpact(UUID failureId) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.<String, Object>of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, AcceptanceKafka.CONTAINER.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "escenario-" + UUID.randomUUID(), ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest", ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false, ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of("traceability.impact"));
            List<ConsumerRecord<String, String>> found = new ArrayList<>();
            Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(100)).alias("PossibleDerivedFailureDetected de la falla " + failureId)
                    .untilAsserted(() -> {
                        consumer.poll(Duration.ofMillis(300)).forEach(record -> {
                            if (failureId.toString().equals(record.key())) {
                                found.add(record);
                            }
                        });
                        assertThat(found).isNotEmpty();
                    });
            assertThat(found).as("una sola publicación por falla").hasSize(1);
            return MAPPER.readTree(found.getFirst().value());
        }
    }

    /** Un escenario empieza con el grafo vacío. */
    public void emptyGraph() {
        neo4j.query("MATCH (n) DETACH DELETE n").run();
    }

    public long count(String cypher) {
        return (Long) neo4j.query(cypher).fetch().one().orElseThrow().get("n");
    }

    public long count(String cypher, Map<String, Object> parameters) {
        return (Long) neo4j.query(cypher).bindAll(parameters).fetch().one().orElseThrow().get("n");
    }

    public Map<String, Object> batch(UUID batchId) {
        return neo4j.query("MATCH (b:Batch {batchId: $id}) RETURN properties(b) AS p").bind(batchId.toString()).to("id").fetch().one()
                .map(row -> castMap(row.get("p"))).orElseThrow(() -> new AssertionError("el lote " + batchId + " no está en el grafo"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
