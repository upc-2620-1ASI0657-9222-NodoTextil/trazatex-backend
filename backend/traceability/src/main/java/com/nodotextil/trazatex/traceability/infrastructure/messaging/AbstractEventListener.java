package com.nodotextil.trazatex.traceability.infrastructure.messaging;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import com.nodotextil.trazatex.sharedmessaging.consumer.EventEnvelopes;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.sharedmessaging.consumer.MalformedEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.function.Consumer;

abstract class AbstractEventListener {

    private static final Logger log = LoggerFactory.getLogger(AbstractEventListener.class);

    private final IdempotentEventHandler handler;
    private final TransientRetry retry;
    protected final ObjectMapper objectMapper;

    AbstractEventListener(IdempotentEventHandler handler, TransientRetry retry, ObjectMapper objectMapper) {
        this.handler = handler;
        this.retry = retry;
        this.objectMapper = objectMapper;
    }

    abstract Map<String, Consumer<BaseDomainEvent>> effects();

    final void consume(ConsumerRecord<String, String> record) {
        BaseDomainEvent event = EventEnvelopes.parse(objectMapper, record.value());
        Consumer<BaseDomainEvent> effect = effects().get(event.type());
        if (effect == null) {
            log.debug("Evento {} ({}) de {} no es de traceability: se ignora", event.eventId(), event.type(), record.topic());
            return;
        }
        retry.run(() -> handler.handle(event, e -> {
            try {
                effect.accept(e);
            } catch (TrazatexException | IllegalArgumentException invalid) {
                throw new MalformedEventException("El payload de " + e.type() + " (" + e.eventId() + ") no es válido: " + invalid.getMessage(), invalid);
            }
        }));
    }

    final <T> T payload(BaseDomainEvent event, Class<T> type) {
        return EventEnvelopes.payload(objectMapper, event, type);
    }
}
