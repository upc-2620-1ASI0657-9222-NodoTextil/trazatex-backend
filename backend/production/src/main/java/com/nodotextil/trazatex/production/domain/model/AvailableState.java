package com.nodotextil.trazatex.production.domain.model;

/** {@code AVAILABLE}: dividir, transformar y transferir exigen un lote habilitado; el control y el descarte, no. */
final class AvailableState implements BatchPhaseState {

    @Override
    public BatchPhase phase() {
        return BatchPhase.AVAILABLE;
    }

    @Override
    public BatchPhase split(Batch batch) {
        batch.ensureEnabled();
        return BatchPhase.SPLIT;
    }

    @Override
    public BatchPhase startTransformation(Batch batch) {
        batch.ensureEnabled();
        return BatchPhase.IN_TRANSFORMATION;
    }

    @Override
    public BatchPhase startTransfer(Batch batch) {
        batch.ensureEnabled();
        return BatchPhase.IN_TRANSFER;
    }

    /** No exige "habilitado": un lote retenido debe poder controlarse. Un producto final no participa (RF-036). */
    @Override
    public BatchPhase startEvaluation(Batch batch) {
        batch.ensureNotFinalProduct();
        return BatchPhase.IN_QUALITY_CONTROL;
    }

    @Override
    public BatchPhase discard(Batch batch) {
        return BatchPhase.DISCARDED;
    }

    @Override
    public void ensureCanChangeQualityHold(Batch batch) {
        // admitido
    }
}
