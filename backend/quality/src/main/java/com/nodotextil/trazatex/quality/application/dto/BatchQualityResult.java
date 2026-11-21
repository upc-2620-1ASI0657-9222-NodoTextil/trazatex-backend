package com.nodotextil.trazatex.quality.application.dto;

import com.nodotextil.trazatex.quality.domain.model.BatchProjection;

import java.util.UUID;

public record BatchQualityResult(UUID batchId, String qualityStatus, boolean qualityHold) {

    public static BatchQualityResult of(BatchProjection batch) {
        return new BatchQualityResult(batch.batchId(), batch.qualityStatus().name(), batch.qualityHold());
    }
}
