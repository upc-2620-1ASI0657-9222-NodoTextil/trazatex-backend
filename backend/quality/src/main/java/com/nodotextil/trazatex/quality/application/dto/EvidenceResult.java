package com.nodotextil.trazatex.quality.application.dto;

import com.nodotextil.trazatex.quality.domain.model.Evidence;
import com.nodotextil.trazatex.quality.domain.model.EvidenceOwnerType;
import com.nodotextil.trazatex.quality.domain.model.EvidenceStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Una evidencia tal como la ve el cliente. {@code url} y {@code uploadedAt} son nulos mientras está {@code PENDING} (o si quedó
 * {@code FAILED}); los intentos y el último error son datos de operación y no se exponen.
 */
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
