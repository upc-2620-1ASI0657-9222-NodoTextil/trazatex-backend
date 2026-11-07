package com.nodotextil.trazatex.production.infrastructure.persistence;
import com.nodotextil.trazatex.production.application.port.out.EvidenceDeletionPort;
import com.nodotextil.trazatex.production.domain.model.EvidenceOwnerType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
@Component
class JdbcEvidenceDeletionAdapter implements EvidenceDeletionPort {
    private final JdbcClient jdbc;
    JdbcEvidenceDeletionAdapter(JdbcClient jdbc) { this.jdbc = jdbc; }
    @Override
    public boolean markDeleted(EvidenceOwnerType type, UUID ownerId, UUID evidenceId, UUID companyId, Instant now) {
        return jdbc.sql("UPDATE evidence SET deleted_at=coalesce(deleted_at,:now) WHERE id=:id AND owner_type=:type AND owner_id=:owner AND company_id=:company")
                .param("now", Timestamps.toDb(now)).param("id", evidenceId).param("type", type.name()).param("owner", ownerId).param("company", companyId).update() == 1;
    }
    @Override
    public Optional<UUID> nextDeleted(Instant now) {
        return jdbc.sql("SELECT id FROM evidence WHERE deleted_at IS NOT NULL AND (status <> 'PENDING' OR next_attempt_at <= :now) AND (purge_retry_at IS NULL OR purge_retry_at <= :now) ORDER BY deleted_at,id LIMIT 1")
                .param("now", Timestamps.toDb(now)).query(UUID.class).optional();
    }
    @Override
    public void deferPurge(UUID id, Instant now) {
        jdbc.sql("UPDATE evidence SET purge_attempts = purge_attempts + 1, purge_retry_at = :now + make_interval(secs => LEAST(300, 10 * power(2, LEAST(purge_attempts, 5)))) WHERE id=:id")
                .param("now", Timestamps.toDb(now)).param("id", id).update();
    }
    @Override
    public void purge(UUID id) {
        jdbc.sql("DELETE FROM evidence_upload_queue WHERE evidence_id=:id").param("id", id).update();
        jdbc.sql("DELETE FROM evidence WHERE id=:id AND deleted_at IS NOT NULL").param("id", id).update();
    }
}
