package com.nodotextil.trazatex.quality.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record FailureConfirmedEvent(
        UUID eventId,
        UUID failureId,
        UUID batchId,
        LocalDateTime occurredAt) {
}
