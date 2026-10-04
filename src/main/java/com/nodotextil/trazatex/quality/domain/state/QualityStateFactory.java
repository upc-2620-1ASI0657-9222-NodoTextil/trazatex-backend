package com.nodotextil.trazatex.quality.domain.state;

import com.nodotextil.trazatex.quality.domain.QualityStatus;

public final class QualityStateFactory {

    private QualityStateFactory() {
    }

    public static QualityState from(QualityStatus status) {
        return switch (status) {
            case NOT_REVIEWED -> new NotReviewedState();
            case CONFORMING -> new ConformingState();
            case FAILED -> new FailedState();
            case PENDING_REEVALUATION -> new PendingReevaluationState();
            case POTENTIAL_DERIVED_FAILURE -> new PotentialDerivedFailureState();
        };
    }
}
