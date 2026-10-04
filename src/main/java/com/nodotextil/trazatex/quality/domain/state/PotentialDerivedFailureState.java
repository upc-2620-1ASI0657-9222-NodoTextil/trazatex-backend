package com.nodotextil.trazatex.quality.domain.state;

import com.nodotextil.trazatex.quality.domain.QualityStatus;

public final class PotentialDerivedFailureState implements QualityState {

    @Override
    public QualityStatus getStatus() {
        return QualityStatus.POTENTIAL_DERIVED_FAILURE;
    }

    @Override
    public QualityState markConforming() {
        return new ConformingState();
    }

    @Override
    public QualityState markFailed() {
        return new FailedState();
    }
}
