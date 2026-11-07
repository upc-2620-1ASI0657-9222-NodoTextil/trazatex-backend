package com.nodotextil.trazatex.traceability.application.dto;

import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;

import java.util.UUID;

import static com.nodotextil.trazatex.traceability.application.dto.EventFields.required;

/** {@code QualityStatusChanged}: sigue el orden de {@code quality.control} (una sola clave por lote). */
public record ApplyQualityStatusCommand(UUID eventId, UUID batchId, QualityStatus newStatus) {

    public ApplyQualityStatusCommand {
        required(eventId, "eventId");
        required(batchId, "batchId");
        required(newStatus, "newStatus");
    }
}
