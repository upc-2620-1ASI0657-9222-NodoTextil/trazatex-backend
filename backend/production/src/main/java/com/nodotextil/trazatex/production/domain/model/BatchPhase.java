package com.nodotextil.trazatex.production.domain.model;

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

    public String label() {
        return label;
    }

    public boolean isHistorical() {
        return historical;
    }
}
