package com.nodotextil.trazatex.sharedmessaging.consumer;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import tools.jackson.databind.ObjectMapper;

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

    public static <T> T payload(ObjectMapper objectMapper, BaseDomainEvent event, Class<T> type) {
        try {
            return objectMapper.convertValue(event.payload(), type);
        } catch (RuntimeException e) {
            throw new MalformedEventException("El payload de " + event.type() + " (" + event.eventId() + ") no es válido", e);
        }
    }
}
