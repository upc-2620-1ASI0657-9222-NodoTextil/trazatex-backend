package com.nodotextil.trazatex.production.domain.model;

/**
 * Estado de calidad (glosario). Lo gobierna Quality: Production guarda una copia solo para mostrarla; las reglas usan
 * {@code qualityHold}, que fija el comando interno.
 */
public enum QualityStatus {
    NOT_REVIEWED,
    CONFORMING,
    FAILED,
    PENDING_REEVALUATION,
    POTENTIAL_DERIVED_FAILURE;

    /** Glosario: {@code FAILED}, {@code PENDING_REEVALUATION} y {@code POTENTIAL_DERIVED_FAILURE} retienen el lote. */
    public boolean retainsBatch() {
        return this == FAILED || this == PENDING_REEVALUATION || this == POTENTIAL_DERIVED_FAILURE;
    }
}
