package com.nodotextil.trazatex.production.infrastructure.event;

import com.nodotextil.trazatex.production.application.event.BatchEventPublisher;
import com.nodotextil.trazatex.production.application.event.BatchSplitEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringBatchEventPublisher implements BatchEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    SpringBatchEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(BatchSplitEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
