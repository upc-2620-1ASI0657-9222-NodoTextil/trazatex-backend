package com.nodotextil.trazatex.traceability.application.view;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record LotView(
        UUID batchId,
        String traceabilityId,
        UUID responsibleCompanyId,
        String materialType,
        String operationalPhase,
        boolean finalProduct,
        LocalDateTime registeredAt,
        Map<String, Object> sharedData) {
}
