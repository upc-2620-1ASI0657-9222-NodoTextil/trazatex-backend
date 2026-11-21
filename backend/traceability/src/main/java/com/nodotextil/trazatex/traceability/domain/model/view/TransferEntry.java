package com.nodotextil.trazatex.traceability.domain.model.view;

import java.time.Instant;
import java.util.UUID;

public record TransferEntry(UUID transferId, UUID toCompanyId, String toBusinessName, Instant at) implements VisibleView {
}
