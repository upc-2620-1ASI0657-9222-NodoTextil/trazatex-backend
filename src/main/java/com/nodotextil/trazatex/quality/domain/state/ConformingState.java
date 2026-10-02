package com.nodotextil.trazatex.quality.domain.state;

import com.nodotextil.trazatex.quality.domain.QualityStatus;

public final class ConformingState implements QualityState {

    @Override
    public QualityStatus getStatus() {
        return QualityStatus.CONFORMING;
    }

    @Override
    public QualityState markPotentialDerivedFailure() {
        return new PotentialDerivedFailureState();
    }
}
