package com.nodotextil.trazatex.traceability.domain.model.impact;

import java.util.UUID;

public record AffectedBatch(UUID batchId, UUID companyId, UUID commonAncestorBatchId) {
}
