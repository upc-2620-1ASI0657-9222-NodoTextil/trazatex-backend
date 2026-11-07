package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.application.port.out.EvidenceRepositoryPort;
import com.nodotextil.trazatex.production.domain.model.Evidence;
import com.nodotextil.trazatex.production.domain.model.EvidenceOwnerType;
import com.nodotextil.trazatex.production.domain.model.EvidenceStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Evidencias y su cola de bytes en PostgreSQL ({@code evidence}, {@code evidence_upload_queue}, V5). */
@Component
class JdbcEvidenceRepositoryAdapter implements EvidenceRepositoryPort {

    private static final String COLUMNS = """
            e.id, e.owner_type, e.owner_id, e.company_id, e.status, e.url, e.original_filename, e.content_type, e.size_bytes,
            e.uploaded_by, e.created_at, e.uploaded_at, e.attempts, e.next_attempt_at, e.last_error""";

    private final JdbcClient jdbc;

    JdbcEvidenceRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveAll(List<NewEvidence> evidences) {
        for (NewEvidence created : evidences) {
            Evidence e = created.evidence();
            jdbc.sql("""
                            INSERT INTO evidence (id, owner_type, owner_id, company_id, status, url, original_filename, content_type,
                                                  size_bytes, uploaded_by, created_at, uploaded_at, attempts, next_attempt_at, last_error)
                            VALUES (:id, :ownerType, :ownerId, :companyId, :status, :url, :filename, :contentType, :size, :uploadedBy,
                                    :createdAt, :uploadedAt, :attempts, :nextAttemptAt, :lastError)
                            """)
                    .param("id", e.id()).param("ownerType", e.ownerType().name()).param("ownerId", e.ownerId())
                    .param("companyId", e.companyId()).param("status", e.status().name()).param("url", e.url())
                    .param("filename", e.originalFilename()).param("contentType", e.contentType()).param("size", e.sizeBytes())
                    .param("uploadedBy", e.uploadedBy()).param("createdAt", Timestamps.toDb(e.createdAt()))
                    .param("uploadedAt", Timestamps.toDb(e.uploadedAt())).param("attempts", e.attempts())
                    .param("nextAttemptAt", Timestamps.toDb(e.nextAttemptAt())).param("lastError", e.lastError())
                    .update();
            if (created.queuedContent() != null) {
                jdbc.sql("INSERT INTO evidence_upload_queue (evidence_id, content) VALUES (:id, :content)")
                        .param("id", e.id()).param("content", created.queuedContent()).update();
            }
        }
    }

    @Override
    public List<Evidence> findByOwnerAndCompany(EvidenceOwnerType ownerType, UUID ownerId, UUID companyId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM evidence e
                        WHERE e.deleted_at IS NULL AND e.owner_type = :ownerType AND e.owner_id = :ownerId AND e.company_id = :companyId
                        ORDER BY e.created_at, e.id
                        """)
                .param("ownerType", ownerType.name()).param("ownerId", ownerId).param("companyId", companyId)
                .query((rs, n) -> map(rs)).list();
    }

    @Override
    public Optional<PendingUpload> claimNextDue(Instant now, Instant leaseUntil) {
        Optional<PendingUpload> claimed = jdbc.sql("SELECT " + COLUMNS + """
                        , q.content
                          FROM evidence e JOIN evidence_upload_queue q ON q.evidence_id = e.id
                         WHERE e.deleted_at IS NULL AND e.status = 'PENDING' AND e.next_attempt_at <= :now
                         ORDER BY e.next_attempt_at, e.id
                         LIMIT 1
                           FOR UPDATE OF e SKIP LOCKED
                        """)
                .param("now", Timestamps.toDb(now))
                .query((rs, n) -> new PendingUpload(map(rs), rs.getBytes("content"))).optional();
        claimed.ifPresent(pending -> jdbc.sql("UPDATE evidence SET next_attempt_at = :lease WHERE id = :id")
                .param("lease", Timestamps.toDb(leaseUntil)).param("id", pending.evidence().id()).update());
        return claimed;
    }

    @Override
    public void update(Evidence e) {
        jdbc.sql("""
                        UPDATE evidence SET status = :status, url = :url, uploaded_at = :uploadedAt, attempts = :attempts,
                                            next_attempt_at = :nextAttemptAt, last_error = :lastError
                         WHERE id = :id AND deleted_at IS NULL
                        """)
                .param("status", e.status().name()).param("url", e.url()).param("uploadedAt", Timestamps.toDb(e.uploadedAt()))
                .param("attempts", e.attempts()).param("nextAttemptAt", Timestamps.toDb(e.nextAttemptAt()))
                .param("lastError", e.lastError()).param("id", e.id()).update();
        if (e.status() == EvidenceStatus.UPLOADED) {
            jdbc.sql("DELETE FROM evidence_upload_queue WHERE evidence_id = :id").param("id", e.id()).update();
        }
    }

    private static Evidence map(ResultSet rs) throws SQLException {
        return Evidence.restore(rs.getObject("id", UUID.class), EvidenceOwnerType.valueOf(rs.getString("owner_type")),
                rs.getObject("owner_id", UUID.class), rs.getObject("company_id", UUID.class),
                EvidenceStatus.valueOf(rs.getString("status")), rs.getString("url"), rs.getString("original_filename"),
                rs.getString("content_type"), rs.getLong("size_bytes"), rs.getObject("uploaded_by", UUID.class),
                Timestamps.fromDb(rs.getObject("created_at", java.time.OffsetDateTime.class)),
                Timestamps.fromDb(rs.getObject("uploaded_at", java.time.OffsetDateTime.class)), rs.getInt("attempts"),
                Timestamps.fromDb(rs.getObject("next_attempt_at", java.time.OffsetDateTime.class)), rs.getString("last_error"));
    }
}
