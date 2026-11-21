package com.nodotextil.trazatex.production.domain.model;

import java.time.Instant;
import java.util.UUID;

public record BatchSnapshot(
        UUID id,
        TraceabilityId traceabilityId,
        QrCode qrCode,
        UUID companyId,
        String name,
        String materialType,
        Quantity quantity,
        Composition composition,
        BatchPhase phase,
        QualityStatus qualityStatus,
        boolean qualityHold,
        boolean finalProduct,
        BatchOriginType originType,
        String origin,
        String supplierName,
        String receptionCharacteristics,
        UUID createdBy,
        Instant createdAt,
        CommercialData commercialData,
        long version) {
}
