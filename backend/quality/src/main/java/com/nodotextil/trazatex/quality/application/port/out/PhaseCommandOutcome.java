package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.model.BatchPhase;

import java.util.UUID;

/**
 * Lo que Production respondió al comando (tabla de «Respuestas y cómo debe reaccionar Quality» en communication.md). <b>No hay un
 * resultado de éxito que se pueda fabricar sin que Production responda 200</b>: sin respuesta es {@link Unavailable}, nunca {@link Confirmed}.
 */
public sealed interface PhaseCommandOutcome {

    /** 200: aplicado, o repetición de un comando ya aplicado (devuelve lo que devolvió la primera vez). */
    record Confirmed(UUID batchId, BatchPhase phase, boolean qualityHold) implements PhaseCommandOutcome {
    }

    /** 409 o 404: rechazo de negocio. Production no aplicó nada; {@code code} es su {@code ApiError.code}. */
    record Rejected(int status, String code, String message) implements PhaseCommandOutcome {
    }

    /** 400 (o cualquier otro 4xx): la petición está mal; es un defecto de Quality y reintentar no lo arregla. Production no aplicó nada. */
    record Defective(int status, String code, String message) implements PhaseCommandOutcome {
    }

    /**
     * 503, otro 5xx, timeout, sin respuesta o circuito abierto: no se sabe si Production aplicó el comando. Se reintenta con el mismo
     * {@code requestId}. {@code circuitOpen}: ni siquiera se llamó (el circuit breaker estaba abierto).
     */
    record Unavailable(String cause, boolean circuitOpen) implements PhaseCommandOutcome {
    }
}
