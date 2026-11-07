package com.nodotextil.trazatex.traceability.domain.model;

/**
 * Relaciones del modelo de la Figura 40. Solo {@code SPLIT_INTO}, {@code INPUT_OF} y {@code PRODUCED} forman la genealogía
 * (lote a lote); {@code OWNS_BATCH} dice quién es el dueño hoy y {@code TRANSFERRED_VIA} es historial: una transferencia
 * no crea lotes ni relaciones padre-hijo (US29, escenario 3).
 */
public enum EdgeType {
    OWNS_BATCH(false),
    INPUT_OF(true),
    PRODUCED(true),
    SPLIT_INTO(true),
    TRANSFERRED_VIA(false);

    private final boolean genealogical;

    EdgeType(boolean genealogical) {
        this.genealogical = genealogical;
    }

    public boolean isGenealogical() {
        return genealogical;
    }
}
