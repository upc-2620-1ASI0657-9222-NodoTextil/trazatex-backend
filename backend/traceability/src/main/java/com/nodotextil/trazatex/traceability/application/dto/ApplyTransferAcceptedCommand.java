package com.nodotextil.trazatex.traceability.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nodotextil.trazatex.traceability.application.dto.EventFields.notEmpty;
import static com.nodotextil.trazatex.traceability.application.dto.EventFields.required;

public record ApplyTransferAcceptedCommand(UUID eventId, UUID transferId, List<UUID> batchIds, UUID toCompanyId, Instant acceptedAt) {

    public ApplyTransferAcceptedCommand {
        required(eventId, "eventId");
        required(transferId, "transferId");
        batchIds = List.copyOf(notEmpty(batchIds, "batchIds"));
        required(toCompanyId, "toCompanyId");
        required(acceptedAt, "acceptedAt");
    }
}
