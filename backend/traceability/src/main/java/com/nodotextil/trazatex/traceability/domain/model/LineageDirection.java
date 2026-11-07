package com.nodotextil.trazatex.traceability.domain.model;

/** Hacia dónde se recorre la genealogía desde el lote consultado. */
public enum LineageDirection {
    /** Hacia atrás: los lotes que le dieron origen (US31). */
    ANCESTORS,
    /** Hacia adelante: los lotes derivados, con sus ramificaciones (US32). */
    DESCENDANTS,
    /** Ambas, con el historial de transferencias de cada lote (US36). */
    BOTH
}
