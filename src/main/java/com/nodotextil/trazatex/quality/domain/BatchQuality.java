package com.nodotextil.trazatex.quality.domain;

import com.nodotextil.trazatex.quality.domain.state.QualityState;
import com.nodotextil.trazatex.quality.domain.state.QualityStateFactory;

import java.util.Objects;
import java.util.UUID;

public final class BatchQuality {

    private final UUID batchId;
    private QualityState state;

    public BatchQuality(UUID batchId) {
        this(batchId, QualityStatus.NOT_REVIEWED);
    }

    public BatchQuality(UUID batchId, QualityStatus status) {
        this.batchId = Objects.requireNonNull(batchId, "Batch id is required");
        this.state = QualityStateFactory.from(
                Objects.requireNonNull(status, "Quality status is required")
        );
    }

    public UUID getBatchId() {
        return batchId;
    }

    public QualityStatus getStatus() {
        return state.getStatus();
    }

    public void markConforming() {
        state = state.markConforming();
    }

    public void markFailed() {
        state = state.markFailed();
    }

    public void requestReevaluation() {
        state = state.requestReevaluation();
    }

    public void markPotentialDerivedFailure() {
        state = state.markPotentialDerivedFailure();
    }
}
