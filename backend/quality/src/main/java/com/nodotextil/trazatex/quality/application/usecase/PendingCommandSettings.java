package com.nodotextil.trazatex.quality.application.usecase;

import java.time.Duration;

public record PendingCommandSettings(Duration grace, int claimsPerRun, Duration lease, Duration initialDelay, double multiplier, Duration maxDelay,
                                   int errorAfterAttempts, Duration holdWaitPoll) {

    public PendingCommandSettings {
        if (multiplier < 1.0 || errorAfterAttempts < 1 || claimsPerRun < 1) {
            throw new IllegalArgumentException("Configuración inválida del reintento de controles: " + this);
        }
    }

    public Duration delayAfter(int attempts) {
        double millis = initialDelay.toMillis() * Math.pow(multiplier, Math.max(0, attempts - 1));
        return Duration.ofMillis((long) Math.min(millis, maxDelay.toMillis()));
    }
}
