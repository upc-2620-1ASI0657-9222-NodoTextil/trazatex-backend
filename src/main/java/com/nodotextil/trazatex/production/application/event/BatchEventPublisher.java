package com.nodotextil.trazatex.production.application.event;

public interface BatchEventPublisher {

    void publish(BatchSplitEvent event);
}
