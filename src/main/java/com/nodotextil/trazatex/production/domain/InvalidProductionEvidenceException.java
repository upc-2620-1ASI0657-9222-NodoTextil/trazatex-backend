package com.nodotextil.trazatex.production.domain;

public class InvalidProductionEvidenceException extends RuntimeException {

    public InvalidProductionEvidenceException(String message) {
        super(message);
    }

    public InvalidProductionEvidenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
