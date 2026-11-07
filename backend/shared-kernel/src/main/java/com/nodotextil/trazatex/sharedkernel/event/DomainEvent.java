package com.nodotextil.trazatex.sharedkernel.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sobre de un evento de dominio (docs/architecture/communication.md). La clave del mensaje en Kafka es {@link #aggregateId()}.
 */
public interface DomainEvent {

    UUID eventId();

    /** Nombre del evento en pasado, igual que el catálogo (por ejemplo {@code BatchRegistered}). */
    String type();

    String aggregateId();

    int version();

    Instant occurredAt();

    Object payload();
}
