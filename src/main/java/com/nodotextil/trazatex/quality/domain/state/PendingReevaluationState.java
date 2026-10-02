package com.nodotextil.trazatex.quality.domain.state;

import com.nodotextil.trazatex.quality.domain.QualityStatus;

public final class PendingReevaluationState implements QualityState {

    @Override
    public QualityStatus getStatus() {
        return QualityStatus.PENDING_REEVALUATION;
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
