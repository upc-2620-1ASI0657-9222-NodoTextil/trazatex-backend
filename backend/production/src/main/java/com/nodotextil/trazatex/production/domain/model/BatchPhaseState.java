package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;

/**
 * Patrón State de la fase del lote (informe 4.1.13). Cada fase decide qué operaciones admite y a qué fase lleva;
 * cualquier otra se rechaza con 409. Los métodos solo <b>calculan</b> la fase siguiente (o lanzan): quien aplica el
 * cambio es {@link Batch}. La tabla de transiciones está en docs/domain/production.md.
 */
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

    /** Activar o quitar la retención por calidad solo se admite sobre un lote {@code AVAILABLE}. */
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
