package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.traceability.integration.EventMessages.Message;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.data.neo4j.core.Neo4jClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Lo común de las pruebas del análisis de impacto con Kafka real: los eventos de Production y de Quality, y mirar el tópico y el grupo del consumidor. */
final class ImpactKafkaSupport {

    static final ObjectMapper JSON = JsonMapper.builder().build();
    static final String GROUP = "traceability.quality-failure";
    static final UUID COMPANY = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    /** Los tópicos que los demás servicios crean antes (el broker no crea tópicos solos). {@code quality.control} no existe: es de Quality y llega en el siguiente sprint. */
    static final Map<String, Integer> FOREIGN_TOPICS = Map.of("production.batch", 1, "production.transformation", 1, "production.transfer", 1, "orgaccess.company", 1,
            "quality.failure", 1);

    private ImpactKafkaSupport() {
    }

    /** {@code FailureConfirmed} de Quality, con todos los campos del catálogo. */
    static Message failureConfirmed(UUID eventId, UUID failure, UUID batch) {
        return EventMessages.of("quality.failure", failure.toString(), eventId, "FailureConfirmed", EventMessages.map("failureId", failure, "batchId", batch,
                "controlId", UUID.randomUUID(), "companyId", COMPANY, "cause", "Hilado irregular", "affectedQuantityKg", 12.5, "confirmedAt", Instant.parse("2026-10-21T10:00:00Z")));
    }

    /**
     * Una transformación (de la empresa A) cuyas entradas son {@code inputs} y cuyas salidas son {@code outputs}; si alguna entrada no se ha registrado, queda
     * provisional: la familia de sus salidas está incompleta.
     */
    static Message transformation(UUID transformation, List<UUID> inputs, List<UUID> outputs) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (UUID output : outputs) {
            list.add(EventMessages.transformationOutput(output, "TT-" + output.toString().substring(0, 8), "QR-" + output.toString().substring(0, 8), "Tela teñida", 40, false));
        }
        return EventMessages.transformationCompleted(UUID.randomUUID(), transformation, "DYEING", COMPANY, "Teñidora", "Teñido", inputs, list, 1, "Orillo", 1,
                GraphBuilder.T0, GraphBuilder.T0.plusSeconds(3600));
    }

    /** El registro del lote y su paso a {@code PROCESSED}: lo que completa a una entrada que había llegado antes de tiempo. */
    static List<Message> registeredAndProcessed(UUID batch) {
        return List.of(EventMessages.batchRegistered(UUID.randomUUID(), batch, "TT-" + batch.toString().substring(0, 8), "QR-" + batch.toString().substring(0, 8), COMPANY,
                "Algodón", 100, "Piura", "Textiles del Norte", "Fibra limpia", GraphBuilder.T0), EventMessages.phaseChanged(UUID.randomUUID(), batch, COMPANY, "AVAILABLE", "PROCESSED", false, 2));
    }

    static void sendAndAwait(KafkaProducer<String, String> producer, Neo4jClient neo4j, Duration patience, List<Message> messages) {
        for (Message message : messages) {
            KafkaTestSupport.send(producer, message);
        }
        GraphAssertions.awaitProcessed(neo4j, messages.stream().map(Message::eventId).toList(), patience);
    }

    /** Lo publicado en {@code traceability.impact} para una falla (la clave es el {@code failureId}). */
    static List<ConsumerRecord<String, String>> impactOf(String bootstrap, UUID failure, Duration wait) {
        return KafkaTestSupport.readAll(bootstrap, "traceability.impact", wait).stream().filter(record -> failure.toString().equals(record.key())).toList();
    }

    /** Los lotes afectados de un {@code PossibleDerivedFailureDetected} como {@code lote → antecesor común}. */
    static Map<UUID, UUID> affectedOf(ConsumerRecord<String, String> record) {
        JsonNode affected = JSON.readTree(record.value()).get("payload").get("affected");
        Map<UUID, UUID> result = new java.util.LinkedHashMap<>();
        for (JsonNode node : affected) {
            result.put(UUID.fromString(node.get("batchId").asString()), UUID.fromString(node.get("commonAncestorBatchId").asString()));
        }
        return result;
    }

    /** Los ids de miembro que el broker ve en el grupo del consumidor del análisis: si lo expulsaran y volviera a unirse, cambiaría. */
    static Set<String> members(String bootstrap) {
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", bootstrap))) {
            ConsumerGroupDescription group = admin.describeConsumerGroups(List.of(GROUP)).all().get(10, TimeUnit.SECONDS).get(GROUP);
            return group.members().stream().map(MemberDescription::consumerId).collect(Collectors.toSet());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String groupState(String bootstrap) {
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", bootstrap))) {
            return admin.describeConsumerGroups(List.of(GROUP)).all().get(10, TimeUnit.SECONDS).get(GROUP).groupState().toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String retentionOf(String bootstrap, String topic) {
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", bootstrap))) {
            var resource = new org.apache.kafka.common.config.ConfigResource(org.apache.kafka.common.config.ConfigResource.Type.TOPIC, topic);
            return admin.describeConfigs(List.of(resource)).all().get(10, TimeUnit.SECONDS).get(resource).get("retention.ms").value();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static int partitionsOf(String bootstrap, String topic) {
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", bootstrap))) {
            return admin.describeTopics(List.of(topic)).allTopicNames().get(10, TimeUnit.SECONDS).get(topic).partitions().size();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
