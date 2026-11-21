package com.nodotextil.trazatex.production.application.port.in;

import com.nodotextil.trazatex.production.application.dto.PhaseTransitionCommand;
import com.nodotextil.trazatex.production.application.dto.PhaseTransitionResult;

import java.util.UUID;

public interface ExecutePhaseTransitionUseCase {

    PhaseTransitionResult execute(UUID batchId, PhaseTransitionCommand command);
}
