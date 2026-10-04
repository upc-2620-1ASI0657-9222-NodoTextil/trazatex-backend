package com.nodotextil.trazatex.quality.domain.state;

import com.nodotextil.trazatex.quality.domain.QualityStatus;

public final class FailedState implements QualityState {

    @Override
    public QualityStatus getStatus() {
        return QualityStatus.FAILED;
    }

    @Override
    public QualityState requestReevaluation() {
        return new PendingReevaluationState();
    }
}
