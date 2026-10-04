package com.nodotextil.trazatex.production.application.contract;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record BatchSnapshot(
        UUID batchId,
        String traceabilityId,
        String qrCode,
        UUID responsibleCompanyId,
        String materialType,
        String operationalPhase,
        boolean finalProduct,
        LocalDateTime registeredAt,
        Map<String, Object> configurableData) {
}
