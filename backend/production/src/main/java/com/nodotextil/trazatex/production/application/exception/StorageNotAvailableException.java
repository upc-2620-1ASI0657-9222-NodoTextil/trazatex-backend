package com.nodotextil.trazatex.production.application.exception;

public class StorageNotAvailableException extends RuntimeException {

    public StorageNotAvailableException(String message) {
        super(message, null, false, false);
    }
}
