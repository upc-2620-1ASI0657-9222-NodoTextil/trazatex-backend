package com.nodotextil.trazatex.sharedkernel.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * Fila de {@code outbox} (docs/architecture/data.md). {@code id} es el {@code eventId} del evento y {@code payload}
 * es el sobre completo ya serializado a JSON.
 */
public record OutboxMessage(
        UUID id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String topic,
        String payload,
        Instant createdAt) {
}
