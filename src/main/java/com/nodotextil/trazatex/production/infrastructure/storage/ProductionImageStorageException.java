package com.nodotextil.trazatex.production.infrastructure.storage;

public class ProductionImageStorageException extends RuntimeException {

    public ProductionImageStorageException(String message) {
        super(message);
    }

    public ProductionImageStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
