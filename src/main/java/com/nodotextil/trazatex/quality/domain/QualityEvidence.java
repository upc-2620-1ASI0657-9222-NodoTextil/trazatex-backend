package com.nodotextil.trazatex.quality.domain;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record QualityEvidence(
        UUID id,
        EvidenceOwnerType ownerType,
        UUID ownerId,
        String publicId,
        String url,
        LocalDateTime uploadedAt) {

    public QualityEvidence {
        Objects.requireNonNull(id, "Evidence id is required");
        Objects.requireNonNull(ownerType, "Evidence owner type is required");
        Objects.requireNonNull(ownerId, "Evidence owner id is required");
        Objects.requireNonNull(publicId, "Evidence public id is required");
        Objects.requireNonNull(url, "Evidence url is required");
        Objects.requireNonNull(uploadedAt, "Evidence upload date is required");
    }
}
