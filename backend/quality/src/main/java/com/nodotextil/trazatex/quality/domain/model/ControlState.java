package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;

sealed interface ControlState permits StartingState, InProgressState, FinishedState, CancelledState {

    ControlStatus status();

    default ControlState confirmStarted() {
        throw invalid("confirmar el inicio");
    }

    default ControlState cancel() {
        throw invalid("cancelar");
    }

    default void requireAcceptsTests() {
        throw notInProgress("agregar pruebas");
    }

    default void requestFinish() {
        throw notInProgress("finalizar");
    }

    default ControlState finish() {
        throw notInProgress("finalizar");
    }

    private ConflictException invalid(String operation) {
        return new ConflictException(ErrorCodes.INVALID_CONTROL_TRANSITION,
                "No se puede " + operation + " un control en estado " + status() + ".");
    }

    default ConflictException notInProgress(String operation) {
        return new ConflictException(ErrorCodes.CONTROL_NOT_IN_PROGRESS,
                "No se puede " + operation + ": el control está en estado " + status() + " y debe estar IN_PROGRESS.");
    }

    static ControlState of(ControlStatus status) {
        return switch (status) {
            case STARTING -> new StartingState();
            case IN_PROGRESS -> new InProgressState();
            case FINISHED -> new FinishedState();
            case CANCELLED -> new CancelledState();
        };
    }
}
