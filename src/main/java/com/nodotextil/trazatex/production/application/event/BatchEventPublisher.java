package com.nodotextil.trazatex.production.application.event;

public interface BatchEventPublisher {

    default void publish(BatchRegisteredEvent event) {
    }

    void publish(BatchSplitEvent event);
}
