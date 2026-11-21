package com.nodotextil.trazatex.traceability.domain.model.graph;

import java.time.Instant;
import java.util.UUID;

public record TransferRecord(UUID transferId, UUID batchId, UUID toCompanyId, Instant at) {
}
