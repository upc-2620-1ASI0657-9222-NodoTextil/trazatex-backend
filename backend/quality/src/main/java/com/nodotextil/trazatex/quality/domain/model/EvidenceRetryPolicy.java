package com.nodotextil.trazatex.quality.domain.model;

import java.time.Duration;

/**
 * Espera creciente entre intentos de subida (ADR-007): {@code initialDelay} × {@code multiplier}^(intentos−1) con tope
 * {@code maxDelay}. Tras {@code maxAttempts} intentos fallidos la evidencia pasa a {@code FAILED}.
 */
public record EvidenceRetryPolicy(int maxAttempts, Duration initialDelay, double multiplier, Duration maxDelay) {

    public static final EvidenceRetryPolicy DEFAULT = new EvidenceRetryPolicy(30, Duration.ofSeconds(30), 2.0, Duration.ofMinutes(30));

    public EvidenceRetryPolicy {
        if (maxAttempts < 1 || multiplier < 1.0 || initialDelay.isNegative() || maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("Política de reintentos inválida");
        }
    }

    /** Espera tras el intento número {@code failedAttempts} (1 = el primero). */
    public Duration delayAfter(int failedAttempts) {
        double millis = initialDelay.toMillis() * Math.pow(multiplier, Math.max(0, failedAttempts - 1));
        return millis >= maxDelay.toMillis() ? maxDelay : Duration.ofMillis((long) millis);
    }
}
