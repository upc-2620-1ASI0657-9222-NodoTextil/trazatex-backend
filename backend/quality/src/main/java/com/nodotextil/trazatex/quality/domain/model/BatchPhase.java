package com.nodotextil.trazatex.quality.domain.model;

/** Fase operativa del lote: la gobierna Production (docs/domain/glossary.md). Quality solo la copia en su proyección. */
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

    /** Lote histórico: solo sirve para reconstruir la genealogía; no se opera. */
    public boolean historic() {
        return historic;
    }
}
