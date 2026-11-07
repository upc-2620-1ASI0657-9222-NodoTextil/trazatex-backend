package com.nodotextil.trazatex.quality.domain.model;

/** Terminal: Production rechazó el inicio; el lote nunca entró en control. */
final class CancelledState implements ControlState {

    @Override
    public ControlStatus status() {
        return ControlStatus.CANCELLED;
    }
}
