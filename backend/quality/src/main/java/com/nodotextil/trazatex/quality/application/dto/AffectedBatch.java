package com.nodotextil.trazatex.quality.application.dto;

import java.util.UUID;

/** Un lote de {@code PossibleDerivedFailureDetected}. */
public record AffectedBatch(UUID batchId, UUID companyId, UUID commonAncestorBatchId) {
}
