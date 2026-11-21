package com.nodotextil.trazatex.quality.domain.model;

import java.util.UUID;

public final class FailureLifecycle {

    private FailureLifecycle() {
    }

    public static FailureChange after(QualityStatus statusBeforeControl, ControlOutcome outcome, QualityFailure openFailure, UUID newFailureId) {
        boolean continuesAnOpenFailure = statusBeforeControl == QualityStatus.PENDING_REEVALUATION && openFailure != null
                && openFailure.status() == FailureStatus.OPEN;
        if (outcome.conforming()) {
            if (continuesAnOpenFailure) {
                openFailure.resolve(outcome);
                return new FailureChange(FailureAction.RESOLVE, openFailure);
            }
            return new FailureChange(FailureAction.NONE, null);
        }
        if (continuesAnOpenFailure) {
            openFailure.reopenAfterFailedReevaluation(outcome);
            return new FailureChange(FailureAction.REOPEN, openFailure);
        }
        return new FailureChange(FailureAction.GENERATE, QualityFailure.generate(newFailureId, outcome));
    }
}
