package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.PhaseOperation;

import java.util.UUID;

public record PhaseTransitionCommand(PhaseOperation operation, String requestId, Boolean qualityHold, String reason, UUID requestedBy) {
}
