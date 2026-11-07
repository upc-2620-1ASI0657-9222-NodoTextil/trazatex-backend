package com.nodotextil.trazatex.production.domain.model;

/** {@code PROCESSED}, {@code SPLIT} y {@code DISCARDED}: estados finales, no admiten ninguna operación. */
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
