package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Una fila de la búsqueda de lotes (US13); el detalle completo está en {@link BatchResult}. */
public record BatchSummaryResult(
        UUID id,
        String traceabilityId,
        String qrCode,
        String name,
        String materialType,
        BigDecimal quantityKg,
        BatchPhase phase,
        QualityStatus qualityStatus,
        boolean qualityHold,
        boolean finalProduct,
        Instant createdAt, String supplierName, String origin) {

    public static BatchSummaryResult from(Batch batch) {
        return new BatchSummaryResult(batch.id(), batch.traceabilityId().value(), batch.qrCode().value(), batch.name(),
                batch.materialType(), batch.quantity().kg(), batch.phase(), batch.qualityStatus(), batch.qualityHold(),
                batch.isFinalProduct(), batch.createdAt(), batch.supplierName(), batch.origin());
    }
}
