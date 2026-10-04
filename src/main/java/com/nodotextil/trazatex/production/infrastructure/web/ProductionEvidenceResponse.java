package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.domain.ProductionEvidence;
import com.nodotextil.trazatex.production.domain.ProductionEvidenceOwnerType;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductionEvidenceResponse(
        UUID id,
        ProductionEvidenceOwnerType ownerType,
        UUID ownerId,
        String url,
        String publicId,
        LocalDateTime uploadedAt,
        UUID uploadedByUserId) {

    static ProductionEvidenceResponse from(ProductionEvidence evidence) {
        return new ProductionEvidenceResponse(
                evidence.id(),
                evidence.ownerType(),
                evidence.ownerId(),
                evidence.url(),
                evidence.publicId(),
                evidence.uploadedAt(),
                evidence.uploadedByUserId());
    }
}
