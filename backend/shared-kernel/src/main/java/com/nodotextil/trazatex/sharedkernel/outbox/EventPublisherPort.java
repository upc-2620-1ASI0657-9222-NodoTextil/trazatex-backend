package com.nodotextil.trazatex.sharedkernel.outbox;

public interface EventPublisherPort {

    void publish(String topic, String key, String payload);
}
