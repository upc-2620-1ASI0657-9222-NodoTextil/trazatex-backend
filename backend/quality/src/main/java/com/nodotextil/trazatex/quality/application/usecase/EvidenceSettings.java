package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.domain.model.EvidenceRetryPolicy;

import java.time.Duration;

/**
 * Parámetros de evidencias ({@code trazatex.evidence.*}): archivos por petición, tamaño máximo y la cola diferida.
 *
 * @param lease             cuánto se reserva una evidencia reclamada por un job mientras se sube (si el job muere, otro la retoma)
 * @param notAvailableDelay cuándo volver a mirar una evidencia cuando Cloudinary no está configurado o su circuito está abierto
 */
public record EvidenceSettings(int maxFiles, long maxFileBytes, int claimsPerRun, Duration lease, Duration notAvailableDelay,
                               EvidenceRetryPolicy retry) {

    public EvidenceSettings {
        if (maxFiles < 1 || maxFileBytes < 1 || claimsPerRun < 1) {
            throw new IllegalArgumentException("Configuración de evidencias inválida");
        }
    }
}
