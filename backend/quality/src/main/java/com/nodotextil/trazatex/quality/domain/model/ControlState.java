package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;

/**
 * Patrón State del control (docs/domain/glossary.md, informe 4.1.13). Cada estado decide qué operaciones admite y a qué estado
 * pasa; lo que no admite lo rechaza con un 409 propio, así que {@link QualityControl} no tiene un solo {@code if} sobre su estado.
 *
 * <pre>
 *  STARTING ──confirmStarted──▶ IN_PROGRESS ──finish──▶ FINISHED
 *     └──cancel──▶ CANCELLED
 * </pre>
 *
 * {@code FINISHED} y {@code CANCELLED} son terminales. Solo {@code IN_PROGRESS} acepta pruebas y finalizar.
 */
sealed interface ControlState permits StartingState, InProgressState, FinishedState, CancelledState {

    ControlStatus status();

    /** Production confirmó {@code START_EVALUATION}. */
    default ControlState confirmStarted() {
        throw invalid("confirmar el inicio");
    }

    /** Production rechazó {@code START_EVALUATION} (409) o ya no puede completarse el inicio. */
    default ControlState cancel() {
        throw invalid("cancelar");
    }

    /** Debe llamarse antes de agregar una prueba. */
    default void requireAcceptsTests() {
        throw notInProgress("agregar pruebas");
    }

    /** Pedir la finalización (el resultado se congela); solo desde {@code IN_PROGRESS}. */
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
