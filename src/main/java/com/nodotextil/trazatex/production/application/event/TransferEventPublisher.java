package com.nodotextil.trazatex.production.application.event;

public interface TransferEventPublisher {

    void publish(TransferStartedEvent event);

    void publish(TransferAcceptedEvent event);

    void publish(TransferRejectedEvent event);
}
