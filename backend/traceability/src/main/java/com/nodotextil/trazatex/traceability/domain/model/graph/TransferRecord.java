package com.nodotextil.trazatex.traceability.domain.model.graph;

import java.time.Instant;
import java.util.UUID;

/** Una entrada del historial de transferencias de un lote ({@code TRANSFERRED_VIA}); no es genealogía. */
public record TransferRecord(UUID transferId, UUID batchId, UUID toCompanyId, Instant at) {
}
