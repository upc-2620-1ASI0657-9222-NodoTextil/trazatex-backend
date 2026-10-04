package com.nodotextil.trazatex.production.infrastructure.event;

import com.nodotextil.trazatex.production.application.event.TransferAcceptedEvent;
import com.nodotextil.trazatex.production.application.event.TransferEventPublisher;
import com.nodotextil.trazatex.production.application.event.TransferRejectedEvent;
import com.nodotextil.trazatex.production.application.event.TransferStartedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringTransferEventPublisher implements TransferEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    SpringTransferEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(TransferStartedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    public void publish(TransferAcceptedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    public void publish(TransferRejectedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
