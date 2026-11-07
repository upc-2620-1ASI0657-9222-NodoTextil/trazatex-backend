package com.nodotextil.trazatex.quality.infrastructure.persistence;

import com.nodotextil.trazatex.quality.application.port.out.BatchProjectionRepositoryPort;
import com.nodotextil.trazatex.quality.domain.model.BatchPhase;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.quality.domain.model.QualityStatus;
import com.nodotextil.trazatex.sharedkernel.paging.PageRequest;
import com.nodotextil.trazatex.sharedkernel.paging.PageResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code batch_projection}: el bloqueo de fila ({@code FOR UPDATE}) serializa los cambios de un mismo lote. */
@Component
class JdbcBatchProjectionAdapter implements BatchProjectionRepositoryPort {

    private static final String COLUMNS = "batch_id, company_id, phase, quality_status, quality_hold, ever_controlled, batch_version, updated_at, traceability_id";

    private final JdbcClient jdbc;

    JdbcBatchProjectionAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<BatchProjection> findByIdForUpdate(UUID batchId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM batch_projection WHERE batch_id = :id FOR UPDATE")
                .param("id", batchId).query(JdbcBatchProjectionAdapter::map).optional();
    }

    @Override
    public Optional<BatchProjection> findById(UUID batchId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM batch_projection WHERE batch_id = :id")
                .param("id", batchId).query(JdbcBatchProjectionAdapter::map).optional();
    }

    @Override
    public PageResult<BatchProjection> findQualityPageByCompany(UUID companyId, PageRequest request) {
        List<BatchProjection> items = jdbc.sql("SELECT " + COLUMNS + " FROM batch_projection WHERE company_id = :company AND quality_status IS NOT NULL "
                        + "ORDER BY batch_id LIMIT :limit OFFSET :offset")
                .param("company", companyId).param("limit", request.size()).param("offset", request.offset())
                .query(JdbcBatchProjectionAdapter::map).list();
        long total = jdbc.sql("SELECT count(*) FROM batch_projection WHERE company_id = :company AND quality_status IS NOT NULL")
                .param("company", companyId).query(Long.class).single();
        return PageResult.of(items, request, total);
    }

    @Override
    public void insert(BatchProjection batch) {
        jdbc.sql("""
                        INSERT INTO batch_projection (batch_id, company_id, phase, quality_status, quality_hold, ever_controlled, batch_version, updated_at, traceability_id)
                        VALUES (:id, :company, :phase, :status, :hold, :ever, :version, :updatedAt, :traceability)
                        """)
                .param("id", batch.batchId())
                .param("company", batch.companyId())
                .param("phase", batch.phase().name())
                .param("status", batch.qualityStatus() == null ? null : batch.qualityStatus().name())
                .param("hold", batch.qualityHold())
                .param("ever", batch.everControlled())
                .param("version", batch.batchVersion())
                .param("updatedAt", Timestamps.toDb(batch.updatedAt()))
                .param("traceability", batch.traceabilityId(), java.sql.Types.VARCHAR)
                .update();
    }

    @Override
    public void update(BatchProjection batch) {
        int rows = jdbc.sql("""
                        UPDATE batch_projection SET company_id = :company, phase = :phase, quality_status = :status, quality_hold = :hold,
                               ever_controlled = :ever, batch_version = :version, updated_at = :updatedAt, traceability_id = :traceability
                        WHERE batch_id = :id
                        """)
                .param("id", batch.batchId())
                .param("company", batch.companyId())
                .param("phase", batch.phase().name())
                .param("status", batch.qualityStatus() == null ? null : batch.qualityStatus().name())
                .param("hold", batch.qualityHold())
                .param("ever", batch.everControlled())
                .param("version", batch.batchVersion())
                .param("updatedAt", Timestamps.toDb(batch.updatedAt()))
                .param("traceability", batch.traceabilityId(), java.sql.Types.VARCHAR)
                .update();
        if (rows != 1) {
            throw new IllegalStateException("El lote " + batch.batchId() + " no está en batch_projection: no se puede actualizar");
        }
    }

    private static BatchProjection map(ResultSet rs, int row) throws SQLException {
        String status = rs.getString("quality_status");
        return BatchProjection.restore(rs.getObject("batch_id", UUID.class), rs.getObject("company_id", UUID.class),
                BatchPhase.valueOf(rs.getString("phase")), rs.getBoolean("quality_hold"), status == null ? null : QualityStatus.valueOf(status),
                rs.getBoolean("ever_controlled"), rs.getLong("batch_version"), Timestamps.fromDb(rs.getObject("updated_at", java.time.OffsetDateTime.class)), rs.getString("traceability_id"));
    }
}
