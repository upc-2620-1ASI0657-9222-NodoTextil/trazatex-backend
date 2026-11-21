package com.nodotextil.trazatex.quality.domain.model;

public enum BatchPhase {
    AVAILABLE(false),
    IN_TRANSFORMATION(false),
    IN_TRANSFER(false),
    IN_QUALITY_CONTROL(false),
    PROCESSED(true),
    SPLIT(true),
    DISCARDED(true);

    private final boolean historic;

    BatchPhase(boolean historic) {
        this.historic = historic;
    }

    public boolean historic() {
        return historic;
    }
}
