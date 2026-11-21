package com.nodotextil.trazatex.apigateway.domain.exception;

public class AuthenticationUnavailableException extends RuntimeException {

    public AuthenticationUnavailableException(String message) {
        super(message);
    }

    public AuthenticationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
