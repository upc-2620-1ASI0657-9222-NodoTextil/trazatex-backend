package com.nodotextil.trazatex.quality.domain.model;

final class CancelledState implements ControlState {

    @Override
    public ControlStatus status() {
        return ControlStatus.CANCELLED;
    }
}
