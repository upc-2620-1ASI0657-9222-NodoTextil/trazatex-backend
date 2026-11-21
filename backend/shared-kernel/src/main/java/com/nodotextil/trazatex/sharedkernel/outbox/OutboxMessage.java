package com.nodotextil.trazatex.sharedkernel.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxMessage(
        UUID id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String topic,
        String payload,
        Instant createdAt) {
}
