package com.nodotextil.trazatex.traceability.application.port;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record LotInfo(
        UUID batchId,
        String traceabilityId,
        String qrCode,
        UUID responsibleCompanyId,
        String materialType,
        String operationalPhase,
        String qualityStatus,
        boolean finalProduct,
        LocalDateTime registeredAt,
        Map<String, Object> configurableData) {

    private static final String AVAILABLE = "AVAILABLE";

    public LotInfo(
            UUID batchId,
            String traceabilityId,
            String qrCode,
            UUID responsibleCompanyId,
            String materialType,
            String operationalPhase,
            boolean finalProduct,
            LocalDateTime registeredAt,
            Map<String, Object> configurableData) {
        this(
                batchId,
                traceabilityId,
                qrCode,
                responsibleCompanyId,
                materialType,
                operationalPhase,
                "NOT_REVIEWED",
                finalProduct,
                registeredAt,
                configurableData);
    }

    public boolean isAvailable() {
        return AVAILABLE.equals(operationalPhase);
    }
}
