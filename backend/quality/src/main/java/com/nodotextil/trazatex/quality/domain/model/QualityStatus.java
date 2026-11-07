package com.nodotextil.trazatex.quality.domain.model;

/** Estado de calidad del lote: lo gobierna Quality (docs/domain/glossary.md). */
public enum QualityStatus {
    NOT_REVIEWED(false),
    CONFORMING(false),
    FAILED(true),
    PENDING_REEVALUATION(true),
    POTENTIAL_DERIVED_FAILURE(true);

    private final boolean retainsBatch;

    QualityStatus(boolean retainsBatch) {
        this.retainsBatch = retainsBatch;
    }

    /** Si el estado implica {@code qualityHold = true} en Production. */
    public boolean retainsBatch() {
        return retainsBatch;
    }
}
