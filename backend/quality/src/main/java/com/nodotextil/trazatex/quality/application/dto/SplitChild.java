package com.nodotextil.trazatex.quality.application.dto;

import com.nodotextil.trazatex.quality.domain.model.QualityStatus;

import java.util.UUID;

/** Un hijo de {@code BatchSplit}: de cada hijo la proyección usa su id, el estado de calidad heredado y su Traceability ID. */
public record SplitChild(UUID batchId, QualityStatus qualityStatus, String traceabilityId) {

    public SplitChild(UUID batchId, QualityStatus qualityStatus) {
        this(batchId, qualityStatus, null);
    }
}
