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
        String qualityStatus,
        boolean finalProduct,
        LocalDateTime registeredAt,
        Map<String, Object> sharedData) {

    public LotView(
            UUID batchId,
            String traceabilityId,
            UUID responsibleCompanyId,
            String materialType,
            String operationalPhase,
            boolean finalProduct,
            LocalDateTime registeredAt,
            Map<String, Object> sharedData) {
        this(
                batchId,
                traceabilityId,
                responsibleCompanyId,
                materialType,
                operationalPhase,
                "NOT_REVIEWED",
                finalProduct,
                registeredAt,
                sharedData);
    }
}
