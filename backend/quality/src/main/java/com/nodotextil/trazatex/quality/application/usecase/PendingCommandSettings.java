package com.nodotextil.trazatex.quality.application.usecase;

import java.time.Duration;

/**
 * Los tiempos de los comandos a Production que se reintentan (inicio y finalización de un control, descarte de una falla y retención por falla
 * derivada).
 *
 * @param grace             cuánto se deja a la petición que creó el control para resolverlo antes de que el job pueda reclamarlo
 * @param claimsPerRun      cuántos controles reclama el job por pasada
 * @param lease             arrendamiento de un control reclamado: si el job cae a mitad, otro lo repite al vencer
 * @param initialDelay      espera tras el primer intento sin respuesta; crece × {@code multiplier} por intento
 * @param maxDelay          tope de la espera entre intentos (5 min): <b>no hay tope de intentos</b>, cancelar por cansancio podría
 *                          desincronizar el lote (Production pudo aplicar el comando)
 * @param errorAfterAttempts desde este intento cada fallo se registra como ERROR (ver los procedimientos en docs/progress.md)
 * @param holdWaitPoll      cada cuánto se vuelve a decidir un aviso de falla derivada que espera a que el lote vuelva a {@code AVAILABLE}
 *                          (también lo despierta un {@code BatchPhaseChanged} del lote); no llama a Production
 */
public record PendingCommandSettings(Duration grace, int claimsPerRun, Duration lease, Duration initialDelay, double multiplier, Duration maxDelay,
                                   int errorAfterAttempts, Duration holdWaitPoll) {

    public PendingCommandSettings {
        if (multiplier < 1.0 || errorAfterAttempts < 1 || claimsPerRun < 1) {
            throw new IllegalArgumentException("Configuración inválida del reintento de controles: " + this);
        }
    }

    /** Espera tras el intento número {@code attempts} (1 = el primero que no tuvo respuesta). */
    public Duration delayAfter(int attempts) {
        double millis = initialDelay.toMillis() * Math.pow(multiplier, Math.max(0, attempts - 1));
        return Duration.ofMillis((long) Math.min(millis, maxDelay.toMillis()));
    }
}
