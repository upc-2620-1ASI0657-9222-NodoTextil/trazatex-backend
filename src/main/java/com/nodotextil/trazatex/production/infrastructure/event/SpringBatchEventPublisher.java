package com.nodotextil.trazatex.production.infrastructure.event;

import com.nodotextil.trazatex.production.application.event.BatchEventPublisher;
import com.nodotextil.trazatex.production.application.event.BatchRegisteredEvent;
import com.nodotextil.trazatex.production.application.event.BatchSplitEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringBatchEventPublisher implements BatchEventPublisher {

    private final ApplicationEventPublisher publisher;

    SpringBatchEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(BatchRegisteredEvent event) {
        publisher.publishEvent(event);
    }

    @Override
    public void publish(BatchSplitEvent event) {
        publisher.publishEvent(event);
    }
}
