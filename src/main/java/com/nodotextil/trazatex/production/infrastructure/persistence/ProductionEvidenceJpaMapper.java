package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.ProductionEvidence;

final class ProductionEvidenceJpaMapper {

    private ProductionEvidenceJpaMapper() {
    }

    static ProductionEvidenceJpaEntity toEntity(ProductionEvidence evidence) {
        return new ProductionEvidenceJpaEntity(
                evidence.id(),
                evidence.ownerType(),
                evidence.ownerId(),
                evidence.url(),
                evidence.publicId(),
                evidence.uploadedAt(),
                evidence.uploadedByUserId());
    }

    static ProductionEvidence toDomain(ProductionEvidenceJpaEntity entity) {
        return new ProductionEvidence(
                entity.getId(),
                entity.getOwnerType(),
                entity.getOwnerId(),
                entity.getUrl(),
                entity.getPublicId(),
                entity.getUploadedAt(),
                entity.getUploadedByUserId());
    }
}
