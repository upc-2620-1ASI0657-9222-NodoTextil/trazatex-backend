package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.Evidence;
import com.nodotextil.trazatex.production.domain.model.EvidenceOwnerType;
import com.nodotextil.trazatex.production.domain.model.EvidenceStatus;

import java.time.Instant;
import java.util.UUID;

public record EvidenceResult(
        UUID id,
        EvidenceOwnerType ownerType,
        UUID ownerId,
        EvidenceStatus status,
        String url,
        String contentType,
        long sizeBytes,
        String originalFilename,
        UUID uploadedBy,
        Instant createdAt,
        Instant uploadedAt) {

    public static EvidenceResult from(Evidence evidence) {
        return new EvidenceResult(evidence.id(), evidence.ownerType(), evidence.ownerId(), evidence.status(), evidence.url(),
                evidence.contentType(), evidence.sizeBytes(), evidence.originalFilename(), evidence.uploadedBy(),
                evidence.createdAt(), evidence.uploadedAt());
    }
}
