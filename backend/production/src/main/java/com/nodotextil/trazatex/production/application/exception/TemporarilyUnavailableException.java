package com.nodotextil.trazatex.production.application.exception;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;

public class TemporarilyUnavailableException extends RuntimeException {

    public TemporarilyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public String code() {
        return ErrorCodes.TEMPORARILY_UNAVAILABLE;
    }
}
