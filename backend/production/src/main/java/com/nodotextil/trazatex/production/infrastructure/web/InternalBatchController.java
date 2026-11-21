package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.dto.PhaseTransitionCommand;
import com.nodotextil.trazatex.production.application.dto.PhaseTransitionResult;
import com.nodotextil.trazatex.production.application.port.in.ExecutePhaseTransitionUseCase;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/batches")
class InternalBatchController {

    private final ExecutePhaseTransitionUseCase phaseTransitions;

    InternalBatchController(ExecutePhaseTransitionUseCase phaseTransitions) {
        this.phaseTransitions = phaseTransitions;
    }

    @PostMapping("/{batchId}/phase-transitions")
    PhaseTransitionResult phaseTransition(@PathVariable UUID batchId, @RequestBody PhaseTransitionCommand command) {
        return phaseTransitions.execute(batchId, command);
    }
}
