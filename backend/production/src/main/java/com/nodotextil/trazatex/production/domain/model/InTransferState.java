package com.nodotextil.trazatex.production.domain.model;

final class InTransferState implements BatchPhaseState {

    @Override
    public BatchPhase phase() {
        return BatchPhase.IN_TRANSFER;
    }

    @Override
    public BatchPhase acceptTransfer(Batch batch) {
        return BatchPhase.AVAILABLE;
    }

    @Override
    public BatchPhase rejectTransfer(Batch batch) {
        return BatchPhase.AVAILABLE;
    }
}
