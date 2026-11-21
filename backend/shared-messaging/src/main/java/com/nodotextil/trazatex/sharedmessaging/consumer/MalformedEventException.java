package com.nodotextil.trazatex.sharedmessaging.consumer;

public class MalformedEventException extends NonRetryableEventException {

    public MalformedEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
