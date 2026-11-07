package com.nodotextil.trazatex.quality.application.dto;

import java.util.UUID;

/** Una salida de {@code TransformationCompleted}: nace {@code AVAILABLE} y {@code NOT_REVIEWED}; de ella la proyección usa su id y su Traceability ID. */
public record OutputBatch(UUID batchId, String traceabilityId) {

    public OutputBatch(UUID batchId) {
        this(batchId, null);
    }
}
