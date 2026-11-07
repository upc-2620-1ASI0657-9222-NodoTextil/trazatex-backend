package com.nodotextil.trazatex.traceability.domain.model;

/** Fase operativa del lote (glosario). La gobierna Production; aquí solo se refleja. */
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

    /** Lote histórico: solo sirve para reconstruir la genealogía y nunca se borra (RNF-12). */
    public boolean isHistorical() {
        return historical;
    }
}
