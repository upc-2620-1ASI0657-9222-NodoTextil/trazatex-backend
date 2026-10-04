package com.nodotextil.trazatex.shared.audit;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditRecord(
        UUID id,
        UUID userId,
        UUID companyId,
        String action,
        String entityType,
        String entityId,
        String details,
        LocalDateTime occurredAt) {
}
