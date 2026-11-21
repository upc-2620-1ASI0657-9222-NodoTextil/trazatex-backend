package com.nodotextil.trazatex.sharedkernel.outbox;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

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

    private synchronized Instant nextCreatedAt() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant next = now.isAfter(lastCreatedAt) ? now : lastCreatedAt.plus(1, ChronoUnit.MICROS);
        lastCreatedAt = next;
        return next;
    }
}
