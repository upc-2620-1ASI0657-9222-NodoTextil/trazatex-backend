package com.nodotextil.trazatex.production.infrastructure.event;

import com.nodotextil.trazatex.production.application.event.TransformationCompletedEvent;
import com.nodotextil.trazatex.production.application.event.TransformationEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringTransformationEventPublisher implements TransformationEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    SpringTransformationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(TransformationCompletedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
