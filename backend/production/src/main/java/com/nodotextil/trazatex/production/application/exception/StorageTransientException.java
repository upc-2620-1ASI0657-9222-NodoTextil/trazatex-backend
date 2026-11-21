package com.nodotextil.trazatex.production.application.exception;

public class StorageTransientException extends RuntimeException {

    public StorageTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
