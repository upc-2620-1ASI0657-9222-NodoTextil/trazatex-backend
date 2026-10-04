package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.domain.Transfer;
import com.nodotextil.trazatex.production.domain.TransferStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID sourceCompanyId,
        UUID destinationCompanyId,
        List<UUID> batchIds,
        TransferStatus status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String rejectionReason) {

    static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.id(),
                transfer.sourceCompanyId(),
                transfer.destinationCompanyId(),
                transfer.batchIds(),
                transfer.status(),
                transfer.startedAt(),
                transfer.completedAt(),
                transfer.rejectionReason());
    }
}
