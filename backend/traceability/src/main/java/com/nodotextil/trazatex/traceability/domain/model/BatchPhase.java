package com.nodotextil.trazatex.traceability.domain.model;

public enum BatchPhase {
    AVAILABLE(false),
    IN_TRANSFORMATION(false),
    IN_TRANSFER(false),
    IN_QUALITY_CONTROL(false),
    PROCESSED(true),
    SPLIT(true),
    DISCARDED(true);

    private final boolean historical;

    BatchPhase(boolean historical) {
        this.historical = historical;
    }

    public boolean isHistorical() {
        return historical;
    }
}
