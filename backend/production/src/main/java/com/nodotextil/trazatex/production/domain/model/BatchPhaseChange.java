package com.nodotextil.trazatex.production.domain.model;

import java.util.UUID;

public record BatchPhaseChange(UUID batchId, UUID companyId, BatchPhase previousPhase, BatchPhase newPhase, boolean qualityHold) {
}
