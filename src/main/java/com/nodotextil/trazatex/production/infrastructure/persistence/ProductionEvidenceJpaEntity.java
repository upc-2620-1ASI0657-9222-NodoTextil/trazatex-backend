package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.ProductionEvidenceOwnerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "production", 
        name = "production_evidence",
        indexes = @Index(
                name = "idx_production_evidence_owner",
                columnList = "owner_type, owner_id"))
class ProductionEvidenceJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, updatable = false)
    private ProductionEvidenceOwnerType ownerType;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "url", nullable = false, updatable = false, length = 2048)
    private String url;

    @Column(name = "public_id", nullable = false, updatable = false)
    private String publicId;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "uploaded_by_user_id", nullable = false, updatable = false)
    private UUID uploadedByUserId;

    protected ProductionEvidenceJpaEntity() {
    }

    ProductionEvidenceJpaEntity(
            UUID id,
            ProductionEvidenceOwnerType ownerType,
            UUID ownerId,
            String url,
            String publicId,
            LocalDateTime uploadedAt,
            UUID uploadedByUserId) {
        this.id = id;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.url = url;
        this.publicId = publicId;
        this.uploadedAt = uploadedAt;
        this.uploadedByUserId = uploadedByUserId;
    }

    UUID getId() {
        return id;
    }

    ProductionEvidenceOwnerType getOwnerType() {
        return ownerType;
    }

    UUID getOwnerId() {
        return ownerId;
    }

    String getUrl() {
        return url;
    }

    String getPublicId() {
        return publicId;
    }

    LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    UUID getUploadedByUserId() {
        return uploadedByUserId;
    }
}
