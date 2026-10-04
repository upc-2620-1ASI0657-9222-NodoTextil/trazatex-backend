package com.nodotextil.trazatex.production.domain;

public class InvalidBatchException extends RuntimeException {

    public InvalidBatchException(String message) {
        super(message);
    }
}
