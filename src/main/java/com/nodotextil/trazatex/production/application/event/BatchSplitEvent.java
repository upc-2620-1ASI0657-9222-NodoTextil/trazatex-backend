package com.nodotextil.trazatex.production.application.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record BatchSplitEvent(
        UUID parentBatchId,
        List<UUID> childBatchIds,
        LocalDateTime occurredAt) {

    public BatchSplitEvent {
        Objects.requireNonNull(parentBatchId, "Parent batch id is required");
        childBatchIds = List.copyOf(childBatchIds);
        Objects.requireNonNull(occurredAt, "Occurrence date is required");
    }
}
