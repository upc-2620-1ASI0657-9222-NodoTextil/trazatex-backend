package com.nodotextil.trazatex.quality.application.dto;

import java.util.UUID;

public record OutputBatch(UUID batchId, String traceabilityId) {

    public OutputBatch(UUID batchId) {
        this(batchId, null);
    }
}
