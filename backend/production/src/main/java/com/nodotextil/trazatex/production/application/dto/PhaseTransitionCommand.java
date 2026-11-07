package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.PhaseOperation;

import java.util.UUID;

/**
 * Cuerpo del comando interno {@code POST /internal/batches/{batchId}/phase-transitions} (docs/architecture/communication.md).
 * {@code qualityHold} es {@code Boolean} y no {@code boolean}: debe poder faltar (obligatorio solo en {@code RELEASE}).
 * {@code requestedBy} es obligatorio salvo en {@code HOLD} (falla derivada, la dispara un evento y no una persona).
 */
public record PhaseTransitionCommand(PhaseOperation operation, String requestId, Boolean qualityHold, String reason, UUID requestedBy) {
}
