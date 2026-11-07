package com.nodotextil.trazatex.sharedkernel.event;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementación del sobre de eventos. El {@code payload} es el objeto del evento (record del servicio productor);
 * al deserializar en un consumidor llega como estructura genérica (mapa) y cada consumidor lo convierte al tipo que necesita.
 */
public record BaseDomainEvent(
        UUID eventId,
        String type,
        String aggregateId,
        int version,
        Instant occurredAt,
        Object payload) implements DomainEvent {

    public static final int CURRENT_VERSION = 1;

    public BaseDomainEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(payload, "payload");
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type es obligatorio");
        }
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId es obligatorio");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version debe ser >= 1");
        }
    }

    public static BaseDomainEvent of(String type, String aggregateId, Object payload) {
        return of(type, aggregateId, payload, Clock.systemUTC());
    }

    public static BaseDomainEvent of(String type, String aggregateId, Object payload, Clock clock) {
        return new BaseDomainEvent(UUID.randomUUID(), type, aggregateId, CURRENT_VERSION, Instant.now(clock), payload);
    }
}
