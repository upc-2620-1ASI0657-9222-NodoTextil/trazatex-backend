package com.nodotextil.trazatex.traceability.application.dto;

import java.util.UUID;

import static com.nodotextil.trazatex.traceability.application.dto.EventFields.required;

/** {@code FailureConfirmed}: de todo el payload solo se usan la falla y el lote fallado. */
public record AnalyzeFailureImpactCommand(UUID eventId, UUID failureId, UUID sourceBatchId) {

    public AnalyzeFailureImpactCommand {
        required(eventId, "eventId");
        required(failureId, "failureId");
        required(sourceBatchId, "batchId");
    }
}
