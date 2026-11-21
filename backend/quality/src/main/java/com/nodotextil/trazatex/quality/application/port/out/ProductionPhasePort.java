package com.nodotextil.trazatex.quality.application.port.out;

public interface ProductionPhasePort {

    PhaseCommandOutcome send(PhaseCommand command);
}
