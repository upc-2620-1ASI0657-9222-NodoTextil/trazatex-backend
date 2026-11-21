package com.nodotextil.trazatex.sharedkernel.outbox;

public class EventPublishException extends RuntimeException {

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
