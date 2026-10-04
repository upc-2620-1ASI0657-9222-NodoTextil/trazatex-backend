package com.nodotextil.trazatex.traceability.infrastructure.event;

import com.nodotextil.trazatex.traceability.application.event.PossibleDerivedFailureDetectedEvent;
import com.nodotextil.trazatex.traceability.application.event.TraceabilityEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringTraceabilityEventPublisher implements TraceabilityEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    SpringTraceabilityEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(PossibleDerivedFailureDetectedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
