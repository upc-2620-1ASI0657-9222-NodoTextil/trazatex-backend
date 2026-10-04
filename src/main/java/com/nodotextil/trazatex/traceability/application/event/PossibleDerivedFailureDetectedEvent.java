package com.nodotextil.trazatex.traceability.application.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PossibleDerivedFailureDetectedEvent(
        UUID failureId,
        UUID sourceBatchId,
        List<UUID> affectedBatchIds,
        LocalDateTime occurredAt) {

    public PossibleDerivedFailureDetectedEvent {
        affectedBatchIds = List.copyOf(affectedBatchIds);
    }
}
