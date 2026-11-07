package com.nodotextil.trazatex.sharedmessaging.consumer;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import tools.jackson.databind.ObjectMapper;

/**
 * Lectura del sobre de un evento y de su payload. Todo lo que no se puede leer es {@link MalformedEventException} (a la DLT sin
 * reintentos): ni el sobre ni el payload mejoran al reintentarlos. Sirve para filtrar por {@code type} antes de entrar al
 * {@link IdempotentEventHandler}, de modo que los tipos que un servicio ignora no dejen filas en {@code processed_events}.
 */
public final class EventEnvelopes {

    private EventEnvelopes() {
    }

    public static BaseDomainEvent parse(ObjectMapper objectMapper, String envelopeJson) {
        try {
            return objectMapper.readValue(envelopeJson, BaseDomainEvent.class);
        } catch (RuntimeException e) {
            throw new MalformedEventException("El mensaje no es un sobre de evento válido", e);
        }
    }

    /** El payload convertido al tipo que el consumidor necesita; un campo con un valor imposible (p. ej. un enum desconocido) es ilegible. */
    public static <T> T payload(ObjectMapper objectMapper, BaseDomainEvent event, Class<T> type) {
        try {
            return objectMapper.convertValue(event.payload(), type);
        } catch (RuntimeException e) {
            throw new MalformedEventException("El payload de " + event.type() + " (" + event.eventId() + ") no es válido", e);
        }
    }
}
