package com.nodotextil.trazatex.quality.application.dto;

import java.util.UUID;

public record AffectedBatch(UUID batchId, UUID companyId, UUID commonAncestorBatchId) {
}
