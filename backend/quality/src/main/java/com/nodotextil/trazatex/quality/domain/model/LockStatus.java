package com.nodotextil.trazatex.quality.domain.model;

/** Estado de la retención que un aviso de posible falla derivada pide sobre un lote ({@code ImpactedBatch}). */
public enum LockStatus {
    /** Falta aplicar el {@code HOLD}: se espera la confirmación de Production o que el lote vuelva a {@code AVAILABLE}. */
    PENDING,
    /** El {@code HOLD} se aplicó y se confirmó. */
    APPLIED,
    /** Quality no tiene nada que hacer con el aviso: lote histórico o ya retenido. */
    NO_ACTION
}
