package com.nodotextil.trazatex.traceability.domain;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record LineageEdge(
        UUID parentBatchId,
        UUID childBatchId,
        LineageType type,
        LocalDateTime occurredAt) {

    public LineageEdge {
        Objects.requireNonNull(parentBatchId, "Parent batch id is required");
        Objects.requireNonNull(childBatchId, "Child batch id is required");
        Objects.requireNonNull(type, "Lineage type is required");
        Objects.requireNonNull(occurredAt, "Occurrence date is required");
        if (parentBatchId.equals(childBatchId)) {
            throw new IllegalArgumentException("A batch cannot be its own ancestor");
        }
    }
}
