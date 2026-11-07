package com.nodotextil.trazatex.sharedkernel.outbox;

/** El broker no confirmó la publicación (error, tiempo agotado o interrupción). */
public class EventPublishException extends RuntimeException {

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
