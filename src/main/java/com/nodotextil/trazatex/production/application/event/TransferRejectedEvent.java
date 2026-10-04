package com.nodotextil.trazatex.production.application.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record TransferRejectedEvent(
        UUID transferId,
        UUID sourceCompanyId,
        UUID destinationCompanyId,
        String rejectionReason,
        LocalDateTime occurredAt) {
}
