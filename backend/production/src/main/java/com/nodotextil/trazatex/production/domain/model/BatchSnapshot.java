package com.nodotextil.trazatex.production.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Todos los datos de un lote, para que el adaptador de persistencia lo reconstruya con {@link Batch#restore}. */
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
