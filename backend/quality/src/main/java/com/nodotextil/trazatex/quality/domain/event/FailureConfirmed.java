package com.nodotextil.trazatex.quality.domain.event;

import com.nodotextil.trazatex.quality.domain.model.QualityFailure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Tópico {@code quality.failure}, clave failureId. Lo consumen traceability (análisis de impacto) y analytics. */
public record FailureConfirmed(UUID failureId, UUID batchId, UUID controlId, UUID companyId, String cause, BigDecimal affectedQuantityKg,
                               Instant confirmedAt) implements QualityEvent {

    public static final String TYPE = "FailureConfirmed";

    public static FailureConfirmed of(QualityFailure failure, UUID companyId, Instant confirmedAt) {
        return new FailureConfirmed(failure.id(), failure.batchId(), failure.controlId(), companyId, failure.rootCause(),
                failure.affectedQtyKg(), confirmedAt);
    }

    @Override
    public String eventType() {
        return TYPE;
    }

    @Override
    public String aggregateId() {
        return failureId.toString();
    }
}
