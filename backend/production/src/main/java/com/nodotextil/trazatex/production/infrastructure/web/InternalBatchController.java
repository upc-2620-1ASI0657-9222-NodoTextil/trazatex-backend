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

/**
 * Comando interno de Quality (ADR-009): <b>solo red interna</b>, el Gateway responde 404 a {@code /internal/**}. No lleva
 * identidad (Quality no tiene la del usuario) ni roles: está fuera de {@code /api/**}, así que ni el interceptor de identidad ni
 * el {@code IdempotencyFilter} lo alcanzan; la idempotencia es por {@code (batchId, requestId, operation)}.
 */
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
