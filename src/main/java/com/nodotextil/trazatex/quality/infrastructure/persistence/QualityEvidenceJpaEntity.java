package com.nodotextil.trazatex.quality.infrastructure.persistence;

import com.nodotextil.trazatex.quality.domain.EvidenceOwnerType;
import com.nodotextil.trazatex.quality.domain.QualityEvidence;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "quality", name = "quality_evidence")
class QualityEvidenceJpaEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private EvidenceOwnerType ownerType;

    private UUID ownerId;
    private String publicId;
    private String url;
    private LocalDateTime uploadedAt;

    protected QualityEvidenceJpaEntity() {
    }

    static QualityEvidenceJpaEntity fromDomain(QualityEvidence evidence) {
        QualityEvidenceJpaEntity entity = new QualityEvidenceJpaEntity();
        entity.id = evidence.id();
        entity.ownerType = evidence.ownerType();
        entity.ownerId = evidence.ownerId();
        entity.publicId = evidence.publicId();
        entity.url = evidence.url();
        entity.uploadedAt = evidence.uploadedAt();
        return entity;
    }

    QualityEvidence toDomain() {
        return new QualityEvidence(id, ownerType, ownerId, publicId, url, uploadedAt);
    }
}
