package com.nodotextil.trazatex.production.application.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TransferStartedEvent(
        UUID transferId,
        UUID sourceCompanyId,
        UUID destinationCompanyId,
        List<UUID> batchIds,
        LocalDateTime occurredAt) {

    public TransferStartedEvent {
        batchIds = List.copyOf(batchIds);
    }
}
