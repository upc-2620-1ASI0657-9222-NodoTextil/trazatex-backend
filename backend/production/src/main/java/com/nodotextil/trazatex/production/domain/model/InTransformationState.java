package com.nodotextil.trazatex.production.domain.model;

final class InTransformationState implements BatchPhaseState {

    @Override
    public BatchPhase phase() {
        return BatchPhase.IN_TRANSFORMATION;
    }

    @Override
    public BatchPhase finishTransformation(Batch batch) {
        return BatchPhase.PROCESSED;
    }
}
