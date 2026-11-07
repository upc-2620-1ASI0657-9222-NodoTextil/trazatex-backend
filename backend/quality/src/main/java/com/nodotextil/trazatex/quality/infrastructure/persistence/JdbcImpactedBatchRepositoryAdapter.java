package com.nodotextil.trazatex.quality.infrastructure.persistence;

import com.nodotextil.trazatex.quality.application.port.out.ClaimedHold;
import com.nodotextil.trazatex.quality.application.port.out.ImpactedBatchRepositoryPort;
import com.nodotextil.trazatex.quality.domain.model.ImpactType;
import com.nodotextil.trazatex.quality.domain.model.ImpactedBatch;
import com.nodotextil.trazatex.quality.domain.model.LockStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code impacted_batch}: {@code (failure_id, batch_id)} es la clave, así que el mismo aviso dos veces no duplica. {@code next_attempt_at} solo tiene valor
 * mientras el aviso está {@code PENDING} (lo exige una restricción de la tabla).
 */
@Component
class JdbcImpactedBatchRepositoryAdapter implements ImpactedBatchRepositoryPort {

    private final JdbcClient jdbc;

    JdbcImpactedBatchRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(ImpactedBatch impacted, Instant createdAt, Instant nextAttemptAt) {
        return jdbc.sql("""
                        INSERT INTO impacted_batch (failure_id, batch_id, impact_type, lock_status, created_at, next_attempt_at)
                        VALUES (:failure, :batch, :type, :lock, :createdAt, :next)
                        ON CONFLICT (failure_id, batch_id) DO NOTHING
                        """)
                .param("failure", impacted.failureId())
                .param("batch", impacted.batchId())
                .param("type", impacted.impactType().name())
                .param("lock", impacted.lockStatus().name())
                .param("createdAt", Timestamps.toDb(createdAt))
                .param("next", impacted.lockStatus() == LockStatus.PENDING ? Timestamps.toDb(nextAttemptAt) : null, Types.TIMESTAMP_WITH_TIMEZONE)
                .update() == 1;
    }

    @Override
    public Optional<ImpactedBatch> findForUpdate(UUID failureId, UUID batchId) {
        return jdbc.sql("SELECT failure_id, batch_id, impact_type, lock_status FROM impacted_batch WHERE failure_id = :f AND batch_id = :b FOR UPDATE")
                .param("f", failureId).param("b", batchId).query((rs, n) -> map(rs)).optional();
    }

    @Override
    public void save(ImpactedBatch impacted) {
        jdbc.sql("""
                        UPDATE impacted_batch SET lock_status = :lock, next_attempt_at = CASE WHEN :pending THEN next_attempt_at ELSE NULL END
                        WHERE failure_id = :f AND batch_id = :b
                        """)
                .param("f", impacted.failureId())
                .param("b", impacted.batchId())
                .param("lock", impacted.lockStatus().name())
                .param("pending", impacted.lockStatus() == LockStatus.PENDING)
                .update();
    }

    @Override
    public int markDue(UUID batchId, Instant now) {
        return jdbc.sql("UPDATE impacted_batch SET next_attempt_at = :now WHERE batch_id = :b AND lock_status = 'PENDING' AND next_attempt_at > :now")
                .param("b", batchId)
                .param("now", Timestamps.toDb(now))
                .update();
    }

    @Override
    public List<ClaimedHold> claimPending(Instant now, int limit, Duration lease) {
        return jdbc.sql("""
                        WITH due AS (
                            SELECT failure_id, batch_id FROM impacted_batch
                            WHERE lock_status = 'PENDING' AND next_attempt_at <= :now
                            ORDER BY next_attempt_at, failure_id, batch_id
                            LIMIT :limit
                            FOR UPDATE SKIP LOCKED)
                        UPDATE impacted_batch i SET next_attempt_at = :leaseUntil
                        FROM due WHERE i.failure_id = due.failure_id AND i.batch_id = due.batch_id
                        RETURNING i.failure_id, i.batch_id, i.impact_type, i.lock_status, i.attempts
                        """)
                .param("now", Timestamps.toDb(now))
                .param("limit", limit)
                .param("leaseUntil", Timestamps.toDb(now.plus(lease)))
                .query((rs, n) -> new ClaimedHold(map(rs), rs.getInt("attempts")))
                .list();
    }

    @Override
    public void recordFailedAttempt(UUID failureId, UUID batchId, int attempts, Instant nextAttemptAt, String error) {
        jdbc.sql("""
                        UPDATE impacted_batch SET attempts = :attempts, next_attempt_at = :next, last_error = :error
                        WHERE failure_id = :f AND batch_id = :b AND lock_status = 'PENDING'
                        """)
                .param("f", failureId).param("b", batchId).param("attempts", attempts)
                .param("next", Timestamps.toDb(nextAttemptAt)).param("error", error, Types.VARCHAR)
                .update();
    }

    @Override
    public void reschedule(UUID failureId, UUID batchId, Instant nextAttemptAt) {
        jdbc.sql("UPDATE impacted_batch SET next_attempt_at = :next WHERE failure_id = :f AND batch_id = :b AND lock_status = 'PENDING'")
                .param("f", failureId).param("b", batchId).param("next", Timestamps.toDb(nextAttemptAt)).update();
    }

    @Override
    public List<ImpactedBatch> findByFailure(UUID failureId) {
        return jdbc.sql("SELECT failure_id, batch_id, impact_type, lock_status FROM impacted_batch WHERE failure_id = :f ORDER BY batch_id")
                .param("f", failureId).query((rs, n) -> map(rs)).list();
    }

    private static ImpactedBatch map(ResultSet rs) throws SQLException {
        return ImpactedBatch.restore(rs.getObject("failure_id", UUID.class), rs.getObject("batch_id", UUID.class),
                ImpactType.valueOf(rs.getString("impact_type")), LockStatus.valueOf(rs.getString("lock_status")));
    }
}
