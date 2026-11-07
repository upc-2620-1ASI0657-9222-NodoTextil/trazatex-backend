package com.nodotextil.trazatex.production.domain.model;

final class InQualityControlState implements BatchPhaseState {

    @Override
    public BatchPhase phase() {
        return BatchPhase.IN_QUALITY_CONTROL;
    }

    @Override
    public BatchPhase release(Batch batch) {
        return BatchPhase.AVAILABLE;
    }
}
