package com.nodotextil.trazatex.quality.infrastructure.persistence;

import com.nodotextil.trazatex.sharedkernel.idempotency.IdempotencyRecord;
import com.nodotextil.trazatex.sharedkernel.idempotency.IdempotencyStorePort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

/** Contrato de {@link IdempotencyStorePort}: {@code INSERT ... ON CONFLICT DO NOTHING} con {@code response_status = 0} provisional. */
@Component
class JdbcIdempotencyStoreAdapter implements IdempotencyStorePort {

    private final JdbcClient jdbc;

    JdbcIdempotencyStoreAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean tryReserve(String key, String userId, String requestHash, Instant createdAt) {
        int rows = jdbc.sql("""
                        INSERT INTO idempotency_keys (idempotency_key, user_id, request_hash, response_status, created_at)
                        VALUES (:key, :userId, :hash, 0, :createdAt)
                        ON CONFLICT (idempotency_key) DO NOTHING
                        """)
                .param("key", key)
                .param("userId", userId)
                .param("hash", requestHash)
                .param("createdAt", Timestamps.toDb(createdAt))
                .update();
        return rows == 1;
    }

    @Override
    public Optional<IdempotencyRecord> find(String key) {
        return jdbc.sql("""
                        SELECT idempotency_key, user_id, request_hash, response_status, response_body::text AS response_body, created_at
                        FROM idempotency_keys WHERE idempotency_key = :key
                        """)
                .param("key", key)
                .query((rs, row) -> new IdempotencyRecord(
                        rs.getString("idempotency_key"),
                        rs.getString("user_id"),
                        rs.getString("request_hash"),
                        rs.getInt("response_status"),
                        rs.getString("response_body"),
                        Timestamps.fromDb(rs.getObject("created_at", OffsetDateTime.class))))
                .optional();
    }

    @Override
    public void complete(String key, int responseStatus, String responseBody) {
        jdbc.sql("""
                        UPDATE idempotency_keys SET response_status = :status, response_body = cast(:body AS jsonb)
                        WHERE idempotency_key = :key
                        """)
                .param("status", responseStatus)
                .param("body", responseBody, Types.VARCHAR)
                .param("key", key)
                .update();
    }

    @Override
    public void delete(String key) {
        jdbc.sql("DELETE FROM idempotency_keys WHERE idempotency_key = :key").param("key", key).update();
    }

    @Override
    public int deleteCreatedBefore(Instant cutoff) {
        return jdbc.sql("DELETE FROM idempotency_keys WHERE created_at < :cutoff")
                .param("cutoff", Timestamps.toDb(cutoff))
                .update();
    }
}
