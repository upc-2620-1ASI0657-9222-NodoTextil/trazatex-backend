package com.nodotextil.trazatex.traceability.domain.model.view;

import java.time.Instant;
import java.util.UUID;

/** Una transferencia del historial de un lote, visible para todos (vista completa). */
public record TransferEntry(UUID transferId, UUID toCompanyId, String toBusinessName, Instant at) implements VisibleView {
}
