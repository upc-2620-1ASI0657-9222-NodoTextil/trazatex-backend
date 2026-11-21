package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;

import java.time.Instant;
import java.util.UUID;

public record SharedBatchResult(
        UUID batchId,
        String traceabilityId,
        String qrCode,
        UUID companyId,
        String materialType,
        BatchPhase phase,
        QualityStatus qualityStatus,
        Instant createdAt,
        boolean finalProduct) implements QrView {

    public static SharedBatchResult from(Batch batch) {
        return new SharedBatchResult(batch.id(), batch.traceabilityId().value(), batch.qrCode().value(), batch.companyId(),
                batch.materialType(), batch.phase(), batch.qualityStatus(), batch.createdAt(), batch.isFinalProduct());
    }
}
