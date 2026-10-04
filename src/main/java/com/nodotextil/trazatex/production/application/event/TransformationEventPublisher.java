package com.nodotextil.trazatex.production.application.event;

public interface TransformationEventPublisher {

    void publish(TransformationCompletedEvent event);
}
