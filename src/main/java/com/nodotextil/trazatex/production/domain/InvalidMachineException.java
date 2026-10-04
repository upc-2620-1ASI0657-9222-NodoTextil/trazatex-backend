package com.nodotextil.trazatex.production.domain;

public class InvalidMachineException extends RuntimeException {

    public InvalidMachineException(String message) {
        super(message);
    }
}
