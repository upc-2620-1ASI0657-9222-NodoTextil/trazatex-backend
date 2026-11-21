package com.nodotextil.trazatex.production.domain.model;

final class HistoricalState implements BatchPhaseState {

    private final BatchPhase phase;

    HistoricalState(BatchPhase phase) {
        this.phase = phase;
    }

    @Override
    public BatchPhase phase() {
        return phase;
    }
}
