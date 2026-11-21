package com.nodotextil.trazatex.quality.application.exception;

public class StoragePermanentException extends RuntimeException {

    public StoragePermanentException(String message) {
        super(message, null, false, false);
    }
}
