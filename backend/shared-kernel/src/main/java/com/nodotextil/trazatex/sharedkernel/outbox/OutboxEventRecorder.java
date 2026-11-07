package com.nodotextil.trazatex.sharedkernel.outbox;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Lado de escritura del Transactional Outbox (ADR-004): el caso de uso la llama dentro de su transacción, de modo que
 * la fila del evento se confirma o se revierte junto con el cambio de negocio. Fuera de una transacción falla.
 */
public class OutboxEventRecorder {

    private final OutboxStorePort store;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private Instant lastCreatedAt = Instant.MIN;

    public OutboxEventRecorder(OutboxStorePort store, ObjectMapper objectMapper) {
        this(store, objectMapper, Clock.systemUTC());
    }

    public OutboxEventRecorder(OutboxStorePort store, ObjectMapper objectMapper, Clock clock) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void record(String aggregateType, String topic, BaseDomainEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("El outbox solo se escribe dentro de la transacción del caso de uso");
        }
        store.save(new OutboxMessage(event.eventId(), aggregateType, event.aggregateId(), event.type(), topic,
                objectMapper.writeValueAsString(event), nextCreatedAt()));
    }

    /** Estrictamente creciente (a la microsegundo, la precisión de PostgreSQL) para que el orden de publicación sea el de registro. */
    private synchronized Instant nextCreatedAt() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant next = now.isAfter(lastCreatedAt) ? now : lastCreatedAt.plus(1, ChronoUnit.MICROS);
        lastCreatedAt = next;
        return next;
    }
}
