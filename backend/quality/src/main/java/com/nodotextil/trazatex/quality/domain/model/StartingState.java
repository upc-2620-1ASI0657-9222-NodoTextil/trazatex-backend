package com.nodotextil.trazatex.quality.domain.model;

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
