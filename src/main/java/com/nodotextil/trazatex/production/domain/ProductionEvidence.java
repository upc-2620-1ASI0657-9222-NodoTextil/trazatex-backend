package com.nodotextil.trazatex.production.domain;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record ProductionEvidence(
        UUID id,
        ProductionEvidenceOwnerType ownerType,
        UUID ownerId,
        String url,
        String publicId,
        LocalDateTime uploadedAt,
        UUID uploadedByUserId) {

    public ProductionEvidence {
        Objects.requireNonNull(id, "Evidence id is required");
        Objects.requireNonNull(ownerType, "Evidence owner type is required");
        Objects.requireNonNull(ownerId, "Evidence owner id is required");
        requireText(url, "Evidence URL is required");
        requireText(publicId, "Evidence public id is required");
        Objects.requireNonNull(uploadedAt, "Evidence upload date is required");
        Objects.requireNonNull(uploadedByUserId, "Uploader user id is required");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidProductionEvidenceException(message);
        }
    }
}
