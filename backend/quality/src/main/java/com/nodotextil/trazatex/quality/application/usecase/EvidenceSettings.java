package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.domain.model.EvidenceRetryPolicy;

import java.time.Duration;

public record EvidenceSettings(int maxFiles, long maxFileBytes, int claimsPerRun, Duration lease, Duration notAvailableDelay,
                               EvidenceRetryPolicy retry) {

    public EvidenceSettings {
        if (maxFiles < 1 || maxFileBytes < 1 || claimsPerRun < 1) {
            throw new IllegalArgumentException("Configuración de evidencias inválida");
        }
    }
}
