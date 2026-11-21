package com.nodotextil.trazatex.quality.domain.model;

final class FinishedState implements ControlState {

    @Override
    public ControlStatus status() {
        return ControlStatus.FINISHED;
    }
}
