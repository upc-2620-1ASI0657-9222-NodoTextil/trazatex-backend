package com.nodotextil.trazatex.traceability.infrastructure.persistence;

import com.nodotextil.trazatex.sharedkernel.outbox.OutboxMessage;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxStorePort;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox de Traceability: nodos {@code (:OutboxEvent)} con las mismas propiedades que la tabla {@code outbox}
 * ({@code docs/architecture/data.md}), escritos en la misma transacción de Neo4j que el cambio del grafo.
 *
 * <p>Neo4j no tiene {@code FOR UPDATE SKIP LOCKED}. {@link #findPending} toma el lock de escritura de las filas candidatas
 * (por orden de {@code createdAt}, así dos relays no se interbloquean) y <b>después</b> vuelve a leerlas filtrando por estado:
 * el segundo relay espera a que el primero termine y entonces ya no las ve pendientes, en vez de republicarlas. Costo
 * frente a PostgreSQL: el segundo relay espera en lugar de saltarse las filas. Con una sola instancia no tiene efecto.
 */
@Component
class Neo4jOutboxStoreAdapter implements OutboxStorePort {

    private final Neo4jClient neo4j;

    Neo4jOutboxStoreAdapter(Neo4jClient neo4j) {
        this.neo4j = neo4j;
    }

    @Override
    public void save(OutboxMessage message) {
        neo4j.query("""
                        CREATE (:OutboxEvent {eventId: $eventId, aggregateType: $aggregateType, aggregateId: $aggregateId,
                          eventType: $eventType, topic: $topic, payload: $payload, status: 'PENDING', attempts: 0,
                          createdAt: datetime($createdAt)})
                        """)
                .bind(message.id().toString()).to("eventId")
                .bind(message.aggregateType()).to("aggregateType")
                .bind(message.aggregateId()).to("aggregateId")
                .bind(message.eventType()).to("eventType")
                .bind(message.topic()).to("topic")
                .bind(message.payload()).to("payload")
                .bind(message.createdAt().toString()).to("createdAt")
                .run();
    }

    @Override
    public List<OutboxMessage> findPending(int limit) {
        List<String> claimed = neo4j.query("""
                        MATCH (o:OutboxEvent {status: 'PENDING'})
                        WITH o ORDER BY o.createdAt ASC, o.eventId ASC LIMIT $limit
                        SET o.claimedAt = datetime()
                        RETURN o.eventId AS eventId
                        """)
                .bind((long) limit).to("limit")
                .fetchAs(String.class).mappedBy((types, record) -> record.get("eventId").asString())
                .all().stream().toList();
        if (claimed.isEmpty()) {
            return List.of();
        }
        return neo4j.query("""
                        MATCH (o:OutboxEvent) WHERE o.eventId IN $ids AND o.status = 'PENDING'
                        RETURN o.eventId AS eventId, o.aggregateType AS aggregateType, o.aggregateId AS aggregateId,
                               o.eventType AS eventType, o.topic AS topic, o.payload AS payload, o.createdAt AS createdAt
                        ORDER BY o.createdAt ASC, o.eventId ASC
                        """)
                .bind(claimed).to("ids")
                .fetch().all().stream().map(Neo4jOutboxStoreAdapter::toMessage).toList();
    }

    @Override
    public void markPublished(UUID id, Instant publishedAt) {
        neo4j.query("MATCH (o:OutboxEvent {eventId: $eventId}) SET o.status = 'PUBLISHED', o.publishedAt = datetime($publishedAt)")
                .bind(id.toString()).to("eventId").bind(publishedAt.toString()).to("publishedAt").run();
    }

    @Override
    public void markFailed(UUID id, String error) {
        neo4j.query("MATCH (o:OutboxEvent {eventId: $eventId}) SET o.attempts = o.attempts + 1, o.lastError = $error")
                .bind(id.toString()).to("eventId").bind(error).to("error").run();
    }

    private static OutboxMessage toMessage(Map<String, Object> row) {
        return new OutboxMessage(UUID.fromString((String) row.get("eventId")), (String) row.get("aggregateType"),
                (String) row.get("aggregateId"), (String) row.get("eventType"), (String) row.get("topic"),
                (String) row.get("payload"), toInstant(row.get("createdAt")));
    }

    private static Instant toInstant(Object value) {
        if (value instanceof ZonedDateTime zoned) {
            return zoned.toInstant();
        }
        if (value instanceof OffsetDateTime offset) {
            return offset.toInstant();
        }
        throw new IllegalStateException("createdAt del outbox no es una fecha: " + value);
    }
}
