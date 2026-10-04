package com.nodotextil.trazatex.quality.domain.state;

import com.nodotextil.trazatex.quality.domain.QualityStatus;

public final class NotReviewedState implements QualityState {

    @Override
    public QualityStatus getStatus() {
        return QualityStatus.NOT_REVIEWED;
    }

    @Override
    public QualityState markConforming() {
        return new ConformingState();
    }

    @Override
    public QualityState markFailed() {
        return new FailedState();
    }

    @Override
    public QualityState markPotentialDerivedFailure() {
        return new PotentialDerivedFailureState();
    }
}
