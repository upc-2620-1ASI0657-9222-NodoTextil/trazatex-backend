package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.BatchPhase;

import java.util.UUID;

public record PhaseTransitionResult(UUID batchId, BatchPhase phase, boolean qualityHold) {
}
