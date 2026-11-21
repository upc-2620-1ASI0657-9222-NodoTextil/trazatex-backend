package com.nodotextil.trazatex.sharedkernel.event;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {

    UUID eventId();

    String type();

    String aggregateId();

    int version();

    Instant occurredAt();

    Object payload();
}
