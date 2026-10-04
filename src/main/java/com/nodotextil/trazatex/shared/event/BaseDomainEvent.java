package com.nodotextil.trazatex.shared.event;

import java.time.Instant;
import java.util.UUID;

public abstract class BaseDomainEvent implements DomainEvent {
    private final UUID eventId = UUID.randomUUID();
    private final UUID aggregateId;
    private final int version;
    private final Instant occurredAt = Instant.now();

    protected BaseDomainEvent(UUID aggregateId, int version) {
        this.aggregateId = aggregateId;
        this.version = version;
    }

    @Override public UUID eventId() { return eventId; }
    @Override public UUID aggregateId() { return aggregateId; }
    @Override public int version() { return version; }
    @Override public Instant occurredAt() { return occurredAt; }
    @Override public String type() { return getClass().getSimpleName(); }
}
