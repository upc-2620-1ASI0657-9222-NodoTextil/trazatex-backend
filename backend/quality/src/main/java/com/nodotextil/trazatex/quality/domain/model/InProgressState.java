package com.nodotextil.trazatex.quality.domain.model;

/** {@code EN_CURSO}: el lote está en {@code IN_QUALITY_CONTROL}; acepta pruebas y se puede finalizar. */
final class InProgressState implements ControlState {

    @Override
    public ControlStatus status() {
        return ControlStatus.IN_PROGRESS;
    }

    @Override
    public void requireAcceptsTests() {
        // las acepta
    }

    @Override
    public void requestFinish() {
        // la admite
    }

    @Override
    public ControlState finish() {
        return new FinishedState();
    }
}
