package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.BatchPhase;

import java.util.UUID;

/** La fase resultante y la retención por calidad actual (o la que quedó la primera vez, si el comando se repitió). */
public record PhaseTransitionResult(UUID batchId, BatchPhase phase, boolean qualityHold) {
}
