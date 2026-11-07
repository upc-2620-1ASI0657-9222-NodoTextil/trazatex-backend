package com.nodotextil.trazatex.quality.domain.model;

/** Terminal: el control tiene resultado y no admite cambios. */
final class FinishedState implements ControlState {

    @Override
    public ControlStatus status() {
        return ControlStatus.FINISHED;
    }
}
