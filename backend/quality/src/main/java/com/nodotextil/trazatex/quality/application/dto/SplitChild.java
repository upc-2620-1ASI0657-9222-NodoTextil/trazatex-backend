package com.nodotextil.trazatex.quality.application.dto;

import com.nodotextil.trazatex.quality.domain.model.QualityStatus;

import java.util.UUID;

public record SplitChild(UUID batchId, QualityStatus qualityStatus, String traceabilityId) {

    public SplitChild(UUID batchId, QualityStatus qualityStatus) {
        this(batchId, qualityStatus, null);
    }
}
