package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;

interface BatchPhaseState {

    BatchPhase phase();

    default BatchPhase split(Batch batch) {
        throw refuse("dividir");
    }

    default BatchPhase startTransformation(Batch batch) {
        throw refuse("iniciar una transformación con");
    }

    default BatchPhase finishTransformation(Batch batch) {
        throw refuse("finalizar la transformación de");
    }

    default BatchPhase startTransfer(Batch batch) {
        throw refuse("transferir");
    }

    default BatchPhase acceptTransfer(Batch batch) {
        throw refuse("aceptar la transferencia de");
    }

    default BatchPhase rejectTransfer(Batch batch) {
        throw refuse("rechazar la transferencia de");
    }

    default BatchPhase startEvaluation(Batch batch) {
        throw refuse("iniciar el control de calidad de");
    }

    default BatchPhase release(Batch batch) {
        throw refuse("liberar del control de calidad a");
    }

    default BatchPhase discard(Batch batch) {
        throw refuse("descartar");
    }

    default void ensureCanChangeQualityHold(Batch batch) {
        throw refuse("cambiar la retención por calidad de");
    }

    private ConflictException refuse(String action) {
        return new ConflictException(ErrorCodes.INVALID_PHASE_TRANSITION,
                "No se puede " + action + " un lote en fase \"" + phase().label() + "\".");
    }

    static BatchPhaseState of(BatchPhase phase) {
        return switch (phase) {
            case AVAILABLE -> new AvailableState();
            case IN_TRANSFORMATION -> new InTransformationState();
            case IN_TRANSFER -> new InTransferState();
            case IN_QUALITY_CONTROL -> new InQualityControlState();
            case PROCESSED, SPLIT, DISCARDED -> new HistoricalState(phase);
        };
    }
}
