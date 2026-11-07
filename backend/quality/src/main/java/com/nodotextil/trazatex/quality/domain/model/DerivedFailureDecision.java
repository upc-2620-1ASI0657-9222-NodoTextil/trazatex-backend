package com.nodotextil.trazatex.quality.domain.model;

/**
 * Qué hace Quality con un lote que Traceability marcó como posible falla derivada (US34, decisión del usuario, 2026-10-23). El aviso
 * <b>siempre</b> se registra en {@code ImpactedBatch}; esta decisión dice cuál es su {@code lockStatus} inicial y si se manda el
 * {@code HOLD} ahora. {@code POTENTIAL_DERIVED_FAILURE} implica {@code qualityHold = true}: la calidad solo cambia cuando el
 * {@code HOLD} se confirmó.
 */
public enum DerivedFailureDecision {
    /** Caso A: {@code AVAILABLE} y sin retención. Se envía {@code HOLD} (requestId = failureId); la calidad cambia al confirmarse. */
    HOLD_NOW(LockStatus.PENDING, true, false),
    /**
     * Caso B: fase no histórica distinta de {@code AVAILABLE} (p. ej. una transferencia en curso), o lote que la proyección aún no
     * conoce del todo (sin su estado de calidad). No se envía {@code HOLD} ni se cambia la calidad; cuando llegue un {@code BatchPhaseChanged} con
     * {@code newPhase = AVAILABLE} se vuelve a decidir (al aceptarse la transferencia, el lote queda retenido en la empresa receptora).
     */
    WAIT_FOR_AVAILABLE(LockStatus.PENDING, false, true),
    /** Caso C: fase histórica ({@code PROCESSED}, {@code SPLIT}, {@code DISCARDED}). No se hace nada. */
    NO_ACTION_HISTORIC(LockStatus.NO_ACTION, false, true),
    /** Ya retenido ({@code qualityHold} o {@code FAILED}/{@code PENDING_REEVALUATION}/{@code POTENTIAL_DERIVED_FAILURE}): se conserva su estado. */
    ALREADY_RETAINED(LockStatus.NO_ACTION, false, false);

    private final LockStatus initialLockStatus;
    private final boolean sendsHold;
    private final boolean warns;

    DerivedFailureDecision(LockStatus initialLockStatus, boolean sendsHold, boolean warns) {
        this.initialLockStatus = initialLockStatus;
        this.sendsHold = sendsHold;
        this.warns = warns;
    }

    public LockStatus initialLockStatus() {
        return initialLockStatus;
    }

    /** Si hay que mandar el comando {@code HOLD} a Production ahora. */
    public boolean sendsHold() {
        return sendsHold;
    }

    /** Si el caso se registra con un {@code WARN} (el lote no se pudo retener ahora, o no hay nada que hacer con él). */
    public boolean warns() {
        return warns;
    }
}
