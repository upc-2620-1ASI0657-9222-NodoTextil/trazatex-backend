package com.nodotextil.trazatex.quality.domain.model;

/** Decide, desde la proyección del lote, qué hacer con un aviso de posible falla derivada (ver {@link DerivedFailureDecision}). */
public final class DerivedFailurePolicy {

    private DerivedFailurePolicy() {
    }

    /** @param batch la proyección del lote, o {@code null} si Quality aún no lo conoce */
    public static DerivedFailureDecision decide(BatchProjection batch) {
        if (batch == null) {
            return DerivedFailureDecision.WAIT_FOR_AVAILABLE;
        }
        if (batch.phase().historic()) {
            return DerivedFailureDecision.NO_ACTION_HISTORIC;
        }
        if (batch.phase() != BatchPhase.AVAILABLE) {
            return DerivedFailureDecision.WAIT_FOR_AVAILABLE;
        }
        if (batch.qualityHold()) {
            return DerivedFailureDecision.ALREADY_RETAINED;
        }
        if (batch.qualityStatus() == null) {
            // la fila solo nació de un BatchPhaseChanged: sin su estado de calidad no se manda un HOLD a ciegas
            return DerivedFailureDecision.WAIT_FOR_AVAILABLE;
        }
        return batch.qualityStatus().retainsBatch() ? DerivedFailureDecision.ALREADY_RETAINED : DerivedFailureDecision.HOLD_NOW;
    }
}
