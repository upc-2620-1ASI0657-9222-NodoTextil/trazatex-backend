package com.nodotextil.trazatex.quality.infrastructure.persistence;

import com.nodotextil.trazatex.quality.application.port.out.ClaimedStart;
import com.nodotextil.trazatex.quality.application.port.out.QualityControlRepositoryPort;
import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.quality.domain.model.ControlStatus;
import com.nodotextil.trazatex.quality.domain.model.ControlType;
import com.nodotextil.trazatex.quality.domain.model.QualityControl;
import com.nodotextil.trazatex.quality.domain.model.QualityTest;
import com.nodotextil.trazatex.quality.domain.model.TestResult;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.paging.PageRequest;
import com.nodotextil.trazatex.sharedkernel.paging.PageResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code quality_control} y {@code quality_test}. Las columnas del reintento ({@code attempts}, {@code next_attempt_at}, {@code last_error})
 * son operativas y no forman parte del agregado: solo las tocan {@link #insertStarting}, {@link #claimPending} y los métodos de reintento.
 * {@code next_attempt_at} solo tiene valor mientras el control está {@code STARTING} (lo exige una restricción de la tabla).
 */
@Component
class JdbcQualityControlRepositoryAdapter implements QualityControlRepositoryPort {

    private static final String COLUMNS = """
            id, batch_id, company_id, control_type, status, overall_result, inspector_id, start_date, end_date, internal_observations,
            cancel_reason, attempts, finish_requested_at, finish_requested_by, finish_result""";

    private static final String RETURNING = """
            c.id, c.batch_id, c.company_id, c.control_type, c.status, c.overall_result, c.inspector_id, c.start_date, c.end_date,
            c.internal_observations, c.cancel_reason, c.attempts, c.finish_requested_at, c.finish_requested_by, c.finish_result""";

    private final JdbcClient jdbc;

    JdbcQualityControlRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertStarting(QualityControl control, Instant nextAttemptAt) {
        try {
            jdbc.sql("""
                            INSERT INTO quality_control (id, batch_id, company_id, control_type, status, inspector_id, start_date, internal_observations,
                                                         attempts, next_attempt_at)
                            VALUES (:id, :batch, :company, :type, 'STARTING', :inspector, :start, :observations, 0, :next)
                            """)
                    .param("id", control.id())
                    .param("batch", control.batchId())
                    .param("company", control.companyId())
                    .param("type", control.controlType().name())
                    .param("inspector", control.inspectorId())
                    .param("start", Timestamps.toDb(control.startDate()))
                    .param("observations", control.internalObservations(), Types.VARCHAR)
                    .param("next", Timestamps.toDb(nextAttemptAt))
                    .update();
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("ux_quality_control_active")) {
                throw new ConflictException(ErrorCodes.CONTROL_ALREADY_ACTIVE, "El lote ya tiene un control de calidad activo.");
            }
            throw e;
        }
    }

    @Override
    public Optional<QualityControl> findById(UUID controlId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM quality_control WHERE id = :id").param("id", controlId)
                .query((rs, row) -> map(rs, 0)).optional().map(this::withTests);
    }

    @Override
    public PageResult<QualityControl> findPageByCompany(UUID companyId, PageRequest request) {
        List<QualityControl> controls = jdbc.sql("SELECT " + COLUMNS + " FROM quality_control WHERE company_id = :company ORDER BY start_date DESC, id LIMIT :limit OFFSET :offset")
                .param("company", companyId).param("limit", request.size()).param("offset", request.offset()).query((rs, row) -> map(rs, 0)).list();
        long total = jdbc.sql("SELECT count(*) FROM quality_control WHERE company_id = :company").param("company", companyId).query(Long.class).single();
        return PageResult.of(withTests(controls), request, total);
    }

    @Override
    public Optional<QualityControl> findByIdForUpdate(UUID controlId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM quality_control WHERE id = :id FOR UPDATE").param("id", controlId)
                .query((rs, row) -> map(rs, 0)).optional().map(this::withTests);
    }

    @Override
    public void save(QualityControl control) {
        int rows = jdbc.sql("""
                        UPDATE quality_control SET status = :status, overall_result = :result, end_date = :end, cancel_reason = :reason,
                               finish_requested_at = :finishAt, finish_requested_by = :finishBy, finish_result = :finishResult,
                               next_attempt_at = CASE WHEN :pending THEN next_attempt_at ELSE NULL END
                        WHERE id = :id
                        """)
                .param("id", control.id())
                .param("pending", control.status() == ControlStatus.STARTING || control.finishPending())
                .param("finishAt", Timestamps.toDb(control.finishRequestedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("finishBy", control.finishRequestedBy(), Types.OTHER)
                .param("finishResult", control.finishResult() == null ? null : control.finishResult().name(), Types.VARCHAR)
                .param("status", control.status().name())
                .param("result", control.overallResult() == null ? null : control.overallResult().name(), Types.VARCHAR)
                .param("end", Timestamps.toDb(control.endDate()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("reason", control.cancellationReason(), Types.VARCHAR)
                .update();
        if (rows != 1) {
            throw new IllegalStateException("El control " + control.id() + " no existe: no se puede guardar");
        }
        for (QualityTest test : control.tests()) {
            jdbc.sql("""
                            INSERT INTO quality_test (id, control_id, criterion_name, expected_value, obtained_value, unit, test_result, remarks)
                            VALUES (:id, :control, :criterion, :expected, :obtained, :unit, :result, :remarks)
                            ON CONFLICT (id) DO NOTHING
                            """)
                    .param("id", test.id())
                    .param("control", control.id())
                    .param("criterion", test.criterionName())
                    .param("expected", test.expectedValue())
                    .param("obtained", test.obtainedValue())
                    .param("unit", test.unit())
                    .param("result", test.testResult().name())
                    .param("remarks", test.remarks(), Types.VARCHAR)
                    .update();
        }
    }

    @Override
    public List<ClaimedStart> claimPendingStarts(Instant now, int limit, Duration lease) {
        return claim("status = 'STARTING'", now, limit, lease);
    }

    @Override
    public List<ClaimedStart> claimPendingFinishes(Instant now, int limit, Duration lease) {
        return claim("status = 'IN_PROGRESS' AND finish_requested_at IS NOT NULL", now, limit, lease);
    }

    @Override
    public void saveFinishRequest(QualityControl control, Instant nextAttemptAt) {
        int rows = jdbc.sql("""
                        UPDATE quality_control SET finish_requested_at = :at, finish_requested_by = :by, finish_result = :result,
                               attempts = 0, last_error = NULL, next_attempt_at = :next
                        WHERE id = :id AND status = 'IN_PROGRESS'
                        """)
                .param("id", control.id())
                .param("at", Timestamps.toDb(control.finishRequestedAt()))
                .param("by", control.finishRequestedBy())
                .param("result", control.finishResult().name())
                .param("next", Timestamps.toDb(nextAttemptAt))
                .update();
        if (rows != 1) {
            throw new IllegalStateException("El control " + control.id() + " no está IN_PROGRESS: no se puede guardar la finalización");
        }
    }

    /** Reclama con {@code FOR UPDATE SKIP LOCKED} y arrienda en una sola sentencia: dos instancias nunca toman el mismo control. */
    private List<ClaimedStart> claim(String predicate, Instant now, int limit, Duration lease) {
        return jdbc.sql("""
                        WITH due AS (
                            SELECT id FROM quality_control
                            WHERE %s AND next_attempt_at <= :now
                            ORDER BY next_attempt_at, id
                            LIMIT :limit
                            FOR UPDATE SKIP LOCKED)
                        UPDATE quality_control c SET next_attempt_at = :leaseUntil
                        FROM due WHERE c.id = due.id
                        RETURNING %s
                        """.formatted(predicate, RETURNING))
                .param("now", Timestamps.toDb(now))
                .param("limit", limit)
                .param("leaseUntil", Timestamps.toDb(now.plus(lease)))
                .query((rs, row) -> new ClaimedStart(map(rs, 0), rs.getInt("attempts")))
                .list();
    }

    @Override
    public void recordFailedAttempt(UUID controlId, int attempts, Instant nextAttemptAt, String error) {
        jdbc.sql("UPDATE quality_control SET attempts = :attempts, next_attempt_at = :next, last_error = :error WHERE id = :id AND next_attempt_at IS NOT NULL")
                .param("id", controlId)
                .param("attempts", attempts)
                .param("next", Timestamps.toDb(nextAttemptAt))
                .param("error", error, Types.VARCHAR)
                .update();
    }

    @Override
    public void reschedule(UUID controlId, Instant nextAttemptAt) {
        jdbc.sql("UPDATE quality_control SET next_attempt_at = :next WHERE id = :id AND next_attempt_at IS NOT NULL")
                .param("id", controlId)
                .param("next", Timestamps.toDb(nextAttemptAt))
                .update();
    }

    private QualityControl withTests(QualityControl control) {
        return withTests(List.of(control)).get(0);
    }

    /** Las pruebas de todos los controles con <b>una</b> consulta ({@code control_id IN (...)}), sin N+1. */
    private List<QualityControl> withTests(List<QualityControl> controls) {
        if (controls.isEmpty()) {
            return controls;
        }
        Map<UUID, List<QualityTest>> byControl = new HashMap<>();
        jdbc.sql("""
                        SELECT id, control_id, criterion_name, expected_value, obtained_value, unit, test_result, remarks
                        FROM quality_test WHERE control_id IN (:ids) ORDER BY control_id, seq
                        """)
                .param("ids", controls.stream().map(QualityControl::id).toList())
                .query((rs, row) -> new QualityTest(rs.getObject("id", UUID.class), rs.getObject("control_id", UUID.class), rs.getString("criterion_name"),
                        rs.getString("expected_value"), rs.getString("obtained_value"), rs.getString("unit"),
                        TestResult.valueOf(rs.getString("test_result")), rs.getString("remarks")))
                .list().forEach(test -> byControl.computeIfAbsent(test.controlId(), id -> new ArrayList<>()).add(test));
        return controls.stream().map(control -> restoreWith(control, byControl.getOrDefault(control.id(), List.of()))).toList();
    }

    private static QualityControl restoreWith(QualityControl control, List<QualityTest> tests) {
        if (tests.isEmpty()) {
            return control;
        }
        return QualityControl.restore(control.id(), control.batchId(), control.companyId(), control.controlType(), control.status(),
                control.overallResult(), control.inspectorId(), control.startDate(), control.endDate(), control.internalObservations(),
                control.cancellationReason(), control.finishRequestedAt(), control.finishRequestedBy(), control.finishResult(), tests);
    }

    private static QualityControl map(ResultSet rs, int ignored) throws SQLException {
        String result = rs.getString("overall_result");
        return QualityControl.restore(rs.getObject("id", UUID.class), rs.getObject("batch_id", UUID.class), rs.getObject("company_id", UUID.class),
                ControlType.valueOf(rs.getString("control_type")), ControlStatus.valueOf(rs.getString("status")),
                result == null ? null : TestResult.valueOf(result), rs.getObject("inspector_id", UUID.class),
                Timestamps.fromDb(rs.getObject("start_date", OffsetDateTime.class)), Timestamps.fromDb(rs.getObject("end_date", OffsetDateTime.class)),
                rs.getString("internal_observations"), rs.getString("cancel_reason"),
                Timestamps.fromDb(rs.getObject("finish_requested_at", OffsetDateTime.class)), rs.getObject("finish_requested_by", UUID.class),
                rs.getString("finish_result") == null ? null : TestResult.valueOf(rs.getString("finish_result")), List.of());
    }
}
