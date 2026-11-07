package com.nodotextil.trazatex.quality.infrastructure.persistence;

import com.nodotextil.trazatex.quality.application.port.out.ClaimedDiscard;
import com.nodotextil.trazatex.quality.application.port.out.QualityFailureRepositoryPort;
import com.nodotextil.trazatex.quality.domain.model.FailureStatus;
import com.nodotextil.trazatex.quality.domain.model.QualityFailure;
import com.nodotextil.trazatex.sharedkernel.paging.PageRequest;
import com.nodotextil.trazatex.sharedkernel.paging.PageResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code quality_failure}. Las columnas del reintento ({@code attempts}, {@code next_attempt_at}, {@code last_error}) son operativas: solo tienen valor mientras
 * el descarte está pendiente (lo exige una restricción de la tabla). Un lote tiene a lo sumo una falla {@code OPEN} (índice único parcial).
 */
@Component
class JdbcQualityFailureRepositoryAdapter implements QualityFailureRepositoryPort {

    private static final String COLUMNS = """
            id, control_id, batch_id, company_id, root_cause, affected_qty_kg, observations, status, discard_requested_at, discard_requested_by, attempts""";

    private final JdbcClient jdbc;

    JdbcQualityFailureRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(QualityFailure failure, Instant createdAt) {
        jdbc.sql("""
                        INSERT INTO quality_failure (id, control_id, batch_id, company_id, root_cause, affected_qty_kg, observations, status, created_at)
                        VALUES (:id, :control, :batch, :company, :cause, :qty, :observations, :status, :createdAt)
                        """)
                .param("id", failure.id())
                .param("control", failure.controlId())
                .param("batch", failure.batchId())
                .param("company", failure.companyId())
                .param("cause", failure.rootCause(), Types.VARCHAR)
                .param("qty", failure.affectedQtyKg(), Types.NUMERIC)
                .param("observations", failure.observations(), Types.VARCHAR)
                .param("status", failure.status().name())
                .param("createdAt", Timestamps.toDb(createdAt))
                .update();
    }

    @Override
    public Optional<QualityFailure> findById(UUID failureId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM quality_failure WHERE id = :id").param("id", failureId).query((rs, n) -> map(rs)).optional();
    }

    @Override
    public Optional<QualityFailure> findByControlId(UUID controlId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM quality_failure WHERE control_id = :id").param("id", controlId).query((rs, n) -> map(rs)).optional();
    }

    @Override
    public PageResult<QualityFailure> findPageByCompany(UUID companyId, PageRequest request) {
        List<QualityFailure> items = jdbc.sql("SELECT " + COLUMNS + " FROM quality_failure WHERE company_id = :company ORDER BY created_at DESC, id LIMIT :limit OFFSET :offset")
                .param("company", companyId).param("limit", request.size()).param("offset", request.offset()).query((rs, n) -> map(rs)).list();
        long total = jdbc.sql("SELECT count(*) FROM quality_failure WHERE company_id = :company").param("company", companyId).query(Long.class).single();
        return PageResult.of(items, request, total);
    }

    @Override
    public Optional<QualityFailure> findByIdForUpdate(UUID failureId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM quality_failure WHERE id = :id FOR UPDATE").param("id", failureId).query((rs, n) -> map(rs)).optional();
    }

    @Override
    public Optional<QualityFailure> findOpenByBatchForUpdate(UUID batchId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM quality_failure WHERE batch_id = :batch AND status = 'OPEN' FOR UPDATE").param("batch", batchId)
                .query((rs, n) -> map(rs)).optional();
    }

    @Override
    public void save(QualityFailure failure) {
        int rows = jdbc.sql("""
                        UPDATE quality_failure SET control_id = :control, root_cause = :cause, affected_qty_kg = :qty, observations = :observations, status = :status,
                               discard_requested_at = :discardAt, discard_requested_by = :discardBy,
                               next_attempt_at = CASE WHEN :pending THEN next_attempt_at ELSE NULL END
                        WHERE id = :id
                        """)
                .param("id", failure.id())
                .param("control", failure.controlId())
                .param("cause", failure.rootCause(), Types.VARCHAR)
                .param("qty", failure.affectedQtyKg(), Types.NUMERIC)
                .param("observations", failure.observations(), Types.VARCHAR)
                .param("status", failure.status().name())
                .param("discardAt", Timestamps.toDb(failure.discardRequestedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("discardBy", failure.discardRequestedBy(), Types.OTHER)
                .param("pending", failure.discardPending())
                .update();
        if (rows != 1) {
            throw new IllegalStateException("La falla " + failure.id() + " no existe: no se puede guardar");
        }
    }

    @Override
    public void saveDiscardRequest(QualityFailure failure, Instant nextAttemptAt) {
        int rows = jdbc.sql("""
                        UPDATE quality_failure SET discard_requested_at = :at, discard_requested_by = :by, attempts = 0, last_error = NULL, next_attempt_at = :next
                        WHERE id = :id AND status = 'OPEN'
                        """)
                .param("id", failure.id())
                .param("at", Timestamps.toDb(failure.discardRequestedAt()))
                .param("by", failure.discardRequestedBy())
                .param("next", Timestamps.toDb(nextAttemptAt))
                .update();
        if (rows != 1) {
            throw new IllegalStateException("La falla " + failure.id() + " no está OPEN: no se puede guardar el descarte");
        }
    }

    @Override
    public List<ClaimedDiscard> claimPendingDiscards(Instant now, int limit, Duration lease) {
        return jdbc.sql("""
                        WITH due AS (
                            SELECT id FROM quality_failure
                            WHERE next_attempt_at <= :now
                            ORDER BY next_attempt_at, id
                            LIMIT :limit
                            FOR UPDATE SKIP LOCKED)
                        UPDATE quality_failure f SET next_attempt_at = :leaseUntil
                        FROM due WHERE f.id = due.id
                        RETURNING f.id, f.control_id, f.batch_id, f.company_id, f.root_cause, f.affected_qty_kg, f.observations, f.status,
                                  f.discard_requested_at, f.discard_requested_by, f.attempts
                        """)
                .param("now", Timestamps.toDb(now))
                .param("limit", limit)
                .param("leaseUntil", Timestamps.toDb(now.plus(lease)))
                .query((rs, n) -> new ClaimedDiscard(map(rs), rs.getInt("attempts")))
                .list();
    }

    @Override
    public void recordFailedAttempt(UUID failureId, int attempts, Instant nextAttemptAt, String error) {
        jdbc.sql("UPDATE quality_failure SET attempts = :attempts, next_attempt_at = :next, last_error = :error WHERE id = :id AND next_attempt_at IS NOT NULL")
                .param("id", failureId)
                .param("attempts", attempts)
                .param("next", Timestamps.toDb(nextAttemptAt))
                .param("error", error, Types.VARCHAR)
                .update();
    }

    @Override
    public void reschedule(UUID failureId, Instant nextAttemptAt) {
        jdbc.sql("UPDATE quality_failure SET next_attempt_at = :next WHERE id = :id AND next_attempt_at IS NOT NULL")
                .param("id", failureId)
                .param("next", Timestamps.toDb(nextAttemptAt))
                .update();
    }

    private static QualityFailure map(ResultSet rs) throws SQLException {
        BigDecimal qty = rs.getBigDecimal("affected_qty_kg");
        return QualityFailure.restore(rs.getObject("id", UUID.class), rs.getObject("control_id", UUID.class), rs.getObject("batch_id", UUID.class),
                rs.getObject("company_id", UUID.class), rs.getString("root_cause"), qty, rs.getString("observations"),
                FailureStatus.valueOf(rs.getString("status")), Timestamps.fromDb(rs.getObject("discard_requested_at", OffsetDateTime.class)),
                rs.getObject("discard_requested_by", UUID.class));
    }
}
