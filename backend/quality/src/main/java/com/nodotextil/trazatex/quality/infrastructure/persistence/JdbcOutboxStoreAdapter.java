package com.nodotextil.trazatex.quality.infrastructure.persistence;

import com.nodotextil.trazatex.sharedkernel.outbox.OutboxMessage;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxStorePort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Contrato de {@link OutboxStorePort}: {@code created_at} lo escribe la aplicación y {@code findPending} usa {@code FOR UPDATE SKIP LOCKED}. */
@Component
class JdbcOutboxStoreAdapter implements OutboxStorePort {

    private final JdbcClient jdbc;

    JdbcOutboxStoreAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(OutboxMessage message) {
        jdbc.sql("""
                        INSERT INTO outbox (id, aggregate_type, aggregate_id, event_type, topic, payload, status, created_at)
                        VALUES (:id, :aggregateType, :aggregateId, :eventType, :topic, cast(:payload AS jsonb), 'PENDING', :createdAt)
                        """)
                .param("id", message.id())
                .param("aggregateType", message.aggregateType())
                .param("aggregateId", message.aggregateId())
                .param("eventType", message.eventType())
                .param("topic", message.topic())
                .param("payload", message.payload())
                .param("createdAt", Timestamps.toDb(message.createdAt()))
                .update();
    }

    @Override
    public List<OutboxMessage> findPending(int limit) {
        return jdbc.sql("""
                        SELECT id, aggregate_type, aggregate_id, event_type, topic, payload::text AS payload, created_at
                        FROM outbox WHERE status = 'PENDING'
                        ORDER BY created_at
                        LIMIT :limit
                        FOR UPDATE SKIP LOCKED
                        """)
                .param("limit", limit)
                .query((rs, row) -> new OutboxMessage(
                        rs.getObject("id", UUID.class),
                        rs.getString("aggregate_type"),
                        rs.getString("aggregate_id"),
                        rs.getString("event_type"),
                        rs.getString("topic"),
                        rs.getString("payload"),
                        Timestamps.fromDb(rs.getObject("created_at", OffsetDateTime.class))))
                .list();
    }

    @Override
    public void markPublished(UUID id, Instant publishedAt) {
        jdbc.sql("UPDATE outbox SET status = 'PUBLISHED', published_at = :at WHERE id = :id")
                .param("at", Timestamps.toDb(publishedAt))
                .param("id", id)
                .update();
    }

    @Override
    public void markFailed(UUID id, String error) {
        jdbc.sql("UPDATE outbox SET attempts = attempts + 1, last_error = :error WHERE id = :id")
                .param("error", error)
                .param("id", id)
                .update();
    }
}
