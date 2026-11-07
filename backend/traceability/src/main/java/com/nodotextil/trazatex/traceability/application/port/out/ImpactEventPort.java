package com.nodotextil.trazatex.traceability.application.port.out;

import com.nodotextil.trazatex.traceability.domain.model.impact.ImpactResult;

import java.util.UUID;

/** Dónde queda el resultado del análisis de impacto: el outbox, en la transacción del caso de uso (solo sale por el evento {@code PossibleDerivedFailureDetected}). */
public interface ImpactEventPort {

    /** ¿Ya se registró {@code PossibleDerivedFailureDetected} para esta falla? Una falla se publica una sola vez, aunque su {@code FailureConfirmed} llegue repetido. */
    boolean alreadyRecorded(UUID failureId);

    /** Registra el evento en el outbox (clave del mensaje: el {@code failureId}). Solo dentro de la transacción del caso de uso. */
    void record(UUID failureId, ImpactResult result);
}
