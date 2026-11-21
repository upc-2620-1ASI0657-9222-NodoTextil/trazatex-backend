package com.nodotextil.trazatex.quality.domain.model;

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

    public boolean retainsBatch() {
        return retainsBatch;
    }
}
