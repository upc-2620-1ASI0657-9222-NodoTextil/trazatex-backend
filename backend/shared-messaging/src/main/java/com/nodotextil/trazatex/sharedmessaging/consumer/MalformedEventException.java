package com.nodotextil.trazatex.sharedmessaging.consumer;

/** El mensaje no es un sobre de evento válido. Reintentarlo no lo arregla: va directo a la DLT. */
public class MalformedEventException extends NonRetryableEventException {

    public MalformedEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
