package com.nodotextil.trazatex.traceability.application.event;

public interface TraceabilityEventPublisher {

    void publish(PossibleDerivedFailureDetectedEvent event);
}
