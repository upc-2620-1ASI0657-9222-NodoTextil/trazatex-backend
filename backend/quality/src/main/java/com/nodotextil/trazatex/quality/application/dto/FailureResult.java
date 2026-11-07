package com.nodotextil.trazatex.quality.application.dto;

import com.nodotextil.trazatex.quality.domain.model.FailureStatus;
import com.nodotextil.trazatex.quality.domain.model.QualityFailure;

import java.math.BigDecimal;
import java.util.UUID;

/** Una falla de la propia empresa. {@code discardPending}: se pidió descartar y Production aún no confirmó {@code DISCARD}. */
public record FailureResult(UUID id, UUID controlId, UUID batchId, UUID companyId, String rootCause, BigDecimal affectedQtyKg, String observations,
                            FailureStatus status, boolean discardPending) {

    public static FailureResult of(QualityFailure failure) {
        return failure == null ? null : new FailureResult(failure.id(), failure.controlId(), failure.batchId(), failure.companyId(), failure.rootCause(),
                failure.affectedQtyKg(), failure.observations(), failure.status(), failure.discardPending());
    }
}
