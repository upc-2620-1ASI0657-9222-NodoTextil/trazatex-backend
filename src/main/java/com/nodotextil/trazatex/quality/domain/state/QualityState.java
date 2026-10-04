package com.nodotextil.trazatex.quality.domain.state;

import com.nodotextil.trazatex.quality.domain.InvalidQualityStateTransitionException;
import com.nodotextil.trazatex.quality.domain.QualityStatus;

public interface QualityState {

    QualityStatus getStatus();

    default QualityState markConforming() {
        throw invalidTransition(QualityStatus.CONFORMING);
    }

    default QualityState markFailed() {
        throw invalidTransition(QualityStatus.FAILED);
    }

    default QualityState requestReevaluation() {
        throw invalidTransition(QualityStatus.PENDING_REEVALUATION);
    }

    default QualityState markPotentialDerivedFailure() {
        throw invalidTransition(QualityStatus.POTENTIAL_DERIVED_FAILURE);
    }

    private InvalidQualityStateTransitionException invalidTransition(QualityStatus target) {
        return new InvalidQualityStateTransitionException(
                "Invalid quality transition from " + getStatus() + " to " + target
        );
    }
}
