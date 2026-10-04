package com.nodotextil.trazatex.shared.event;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
    UUID eventId();
    UUID aggregateId();
    int version();
    Instant occurredAt();
    String type();
}
