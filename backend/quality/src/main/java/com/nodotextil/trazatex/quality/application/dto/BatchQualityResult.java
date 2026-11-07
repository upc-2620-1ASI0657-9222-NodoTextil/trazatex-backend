package com.nodotextil.trazatex.quality.application.dto;

import com.nodotextil.trazatex.quality.domain.model.BatchProjection;

import java.util.UUID;

/** El estado de calidad de un lote de la propia empresa y si está retenido. */
public record BatchQualityResult(UUID batchId, String qualityStatus, boolean qualityHold) {

    public static BatchQualityResult of(BatchProjection batch) {
        return new BatchQualityResult(batch.batchId(), batch.qualityStatus().name(), batch.qualityHold());
    }
}
