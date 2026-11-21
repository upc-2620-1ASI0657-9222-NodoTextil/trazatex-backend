package com.nodotextil.trazatex.quality.infrastructure.messaging;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedmessaging.consumer.MalformedEventException;

final class ProductionEventListeners {

    private ProductionEventListeners() {
    }

    static void require(BaseDomainEvent event, boolean valid, String fields) {
        if (!valid) {
            throw new MalformedEventException("El payload de " + event.type() + " (" + event.eventId() + ") debe traer " + fields, null);
        }
    }
}
