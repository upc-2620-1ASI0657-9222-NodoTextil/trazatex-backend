package com.nodotextil.trazatex.production.domain.model;

/** Fase operativa del lote (glosario): solo Production la gobierna. Los estados históricos son finales. */
public enum BatchPhase {

    AVAILABLE("Disponible", false),
    IN_TRANSFORMATION("En transformación", false),
    IN_TRANSFER("En transferencia", false),
    IN_QUALITY_CONTROL("En control de calidad", false),
    PROCESSED("Procesado", true),
    SPLIT("Dividido", true),
    DISCARDED("Descartado", true);

    private final String label;
    private final boolean historical;

    BatchPhase(String label, boolean historical) {
        this.label = label;
        this.historical = historical;
    }

    /** Nombre para mensajes al usuario. */
    public String label() {
        return label;
    }

    /** Lote histórico: solo sirve para reconstruir la genealogía. */
    public boolean isHistorical() {
        return historical;
    }
}
