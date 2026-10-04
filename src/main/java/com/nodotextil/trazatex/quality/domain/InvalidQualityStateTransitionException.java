package com.nodotextil.trazatex.quality.domain;

public class InvalidQualityStateTransitionException extends RuntimeException {

    public InvalidQualityStateTransitionException(String message) {
        super(message);
    }
}
