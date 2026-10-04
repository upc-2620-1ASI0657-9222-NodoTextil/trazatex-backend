package com.nodotextil.trazatex.production.application.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TransformationCompletedEvent(
        List<UUID> inputBatchIds,
        List<UUID> outputBatchIds,
        LocalDateTime occurredAt) {

    public TransformationCompletedEvent {
        inputBatchIds = List.copyOf(inputBatchIds);
        outputBatchIds = List.copyOf(outputBatchIds);
        Objects.requireNonNull(occurredAt, "Occurrence date is required");
    }
}
