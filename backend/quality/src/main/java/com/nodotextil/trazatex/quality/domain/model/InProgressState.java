package com.nodotextil.trazatex.quality.domain.model;

final class InProgressState implements ControlState {

    @Override
    public ControlStatus status() {
        return ControlStatus.IN_PROGRESS;
    }

    @Override
    public void requireAcceptsTests() {
    }

    @Override
    public void requestFinish() {
    }

    @Override
    public ControlState finish() {
        return new FinishedState();
    }
}
