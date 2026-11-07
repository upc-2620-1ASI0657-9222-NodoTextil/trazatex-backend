package com.nodotextil.trazatex.production.application.port.in;

import com.nodotextil.trazatex.production.application.dto.PhaseTransitionCommand;
import com.nodotextil.trazatex.production.application.dto.PhaseTransitionResult;

import java.util.UUID;

/** Comando interno de Quality → Production (ADR-009): cambia la fase o la retención por calidad de un lote. */
public interface ExecutePhaseTransitionUseCase {

    /**
     * Idempotente por {@code (batchId, requestId, operation)}: repetirlo devuelve el resultado guardado aunque el lote haya
     * cambiado después. Solo un rechazo de negocio es 409; una carrera que no se resuelve es
     * {@link com.nodotextil.trazatex.production.application.exception.TemporarilyUnavailableException} (503), que Quality reintenta.
     */
    PhaseTransitionResult execute(UUID batchId, PhaseTransitionCommand command);
}
