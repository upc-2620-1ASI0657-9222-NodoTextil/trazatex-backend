package com.nodotextil.trazatex.quality.infrastructure.messaging;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedmessaging.consumer.MalformedEventException;

/** Lo común de los listeners de los tópicos de Production. */
final class ProductionEventListeners {

    private ProductionEventListeners() {
    }

    /** Un payload al que le falta un dato obligatorio no se arregla reintentando: {@link MalformedEventException} va a la DLT sin reintentos. */
    static void require(BaseDomainEvent event, boolean valid, String fields) {
        if (!valid) {
            throw new MalformedEventException("El payload de " + event.type() + " (" + event.eventId() + ") debe traer " + fields, null);
        }
    }
}
