package com.nodotextil.trazatex.quality.domain.model;

/** {@code INICIANDO}: el control se guardó y el comando {@code START_EVALUATION} aún no tiene respuesta. */
final class StartingState implements ControlState {

    @Override
    public ControlStatus status() {
        return ControlStatus.STARTING;
    }

    @Override
    public ControlState confirmStarted() {
        return new InProgressState();
    }

    @Override
    public ControlState cancel() {
        return new CancelledState();
    }
}
