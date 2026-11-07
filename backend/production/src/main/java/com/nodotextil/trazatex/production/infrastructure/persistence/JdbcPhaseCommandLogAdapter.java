package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.application.port.out.PhaseCommandLogPort;
import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.PhaseCommandResult;
import com.nodotextil.trazatex.production.domain.model.PhaseOperation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
class JdbcPhaseCommandLogAdapter implements PhaseCommandLogPort {

    private final JdbcClient jdbc;

    JdbcPhaseCommandLogAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PhaseCommandResult> find(UUID batchId, String requestId, PhaseOperation operation) {
        return jdbc.sql("""
                        SELECT result_phase, result_quality_hold FROM phase_command_log
                        WHERE batch_id = :batchId AND request_id = :requestId AND operation = :operation
                        """)
                .param("batchId", batchId).param("requestId", requestId).param("operation", operation.name())
                .query((rs, n) -> new PhaseCommandResult(BatchPhase.valueOf(rs.getString("result_phase")), rs.getBoolean("result_quality_hold")))
                .optional();
    }

    /** INSERT simple: la PK es la barrera ante dos comandos idénticos simultáneos; el perdedor recibe {@code DuplicateKeyException}. */
    @Override
    public void save(UUID batchId, String requestId, PhaseOperation operation, PhaseCommandResult result, Instant at) {
        jdbc.sql("""
                        INSERT INTO phase_command_log (batch_id, request_id, operation, result_phase, result_quality_hold, created_at)
                        VALUES (:batchId, :requestId, :operation, :phase, :hold, :createdAt)
                        """)
                .param("batchId", batchId).param("requestId", requestId).param("operation", operation.name())
                .param("phase", result.phase().name()).param("hold", result.qualityHold())
                .param("createdAt", Timestamps.toDb(at))
                .update();
    }
}
