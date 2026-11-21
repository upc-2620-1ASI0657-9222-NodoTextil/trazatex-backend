package com.nodotextil.trazatex.apigateway.domain.exception;

import java.time.Duration;

public class RateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super("Límite de peticiones agotado", null, false, false);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    public long retryAfterSeconds() {
        long seconds = (retryAfter.toMillis() + 999) / 1000;
        return Math.max(1, seconds);
    }
}
