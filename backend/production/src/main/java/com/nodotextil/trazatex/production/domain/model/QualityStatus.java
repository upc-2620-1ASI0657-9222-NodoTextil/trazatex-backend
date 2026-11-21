package com.nodotextil.trazatex.production.domain.model;

public enum QualityStatus {
    NOT_REVIEWED,
    CONFORMING,
    FAILED,
    PENDING_REEVALUATION,
    POTENTIAL_DERIVED_FAILURE;

    public boolean retainsBatch() {
        return this == FAILED || this == PENDING_REEVALUATION || this == POTENTIAL_DERIVED_FAILURE;
    }
}
