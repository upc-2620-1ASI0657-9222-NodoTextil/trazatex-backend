package com.nodotextil.trazatex.traceability.domain.model.impact;

import java.util.List;
import java.util.UUID;

public record ImpactResult(UUID sourceBatchId, List<AffectedBatch> affected, boolean truncated) {

    public ImpactResult {
        affected = List.copyOf(affected);
    }
}
