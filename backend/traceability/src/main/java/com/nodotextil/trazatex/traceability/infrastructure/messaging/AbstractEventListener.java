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

/**
 * Lo común de los cinco listeners: leer el sobre, <b>filtrar por {@code type} antes</b> del consumo idempotente (lo que
 * traceability ignora —otros eventos del mismo tópico, tipos nuevos— no da error, no va a la DLT y no deja filas
 * {@code :ProcessedEvent}) y aplicar el efecto dentro de la transacción idempotente, repitiéndolo si Neo4j aborta la transacción
 * por un interbloqueo. Un sobre o un payload ilegible es {@link MalformedEventException}: a la DLT sin reintentos.
 */
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

    /** Efecto de cada tipo de evento que este listener atiende. */
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
                // le falta un dato obligatorio o trae uno imposible: reintentar no lo arregla
                throw new MalformedEventException("El payload de " + e.type() + " (" + e.eventId() + ") no es válido: " + invalid.getMessage(), invalid);
            }
        }));
    }

    final <T> T payload(BaseDomainEvent event, Class<T> type) {
        return EventEnvelopes.payload(objectMapper, event, type);
    }
}
