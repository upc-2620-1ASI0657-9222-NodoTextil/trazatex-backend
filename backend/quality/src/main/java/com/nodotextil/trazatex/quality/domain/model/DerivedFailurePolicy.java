package com.nodotextil.trazatex.quality.domain.model;

public final class DerivedFailurePolicy {

    private DerivedFailurePolicy() {
    }

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
            return DerivedFailureDecision.WAIT_FOR_AVAILABLE;
        }
        return batch.qualityStatus().retainsBatch() ? DerivedFailureDecision.ALREADY_RETAINED : DerivedFailureDecision.HOLD_NOW;
    }
}
