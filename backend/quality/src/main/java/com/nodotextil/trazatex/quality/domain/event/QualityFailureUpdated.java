package com.nodotextil.trazatex.quality.domain.event;

import com.nodotextil.trazatex.quality.domain.model.QualityFailure;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Agregado en V2. Tópico {@code quality.failure}, clave failureId. Se publica cada vez que una falla se genera, reabre o resuelve al finalizar un
 * control, cuando se completa su información y cuando Production confirma su descarte. Lo consumen traceability (causa de falla, campo
 * configurable {@code FAILURE_CAUSE}) y analytics.
 */
public record QualityFailureUpdated(UUID failureId,UUID batchId,UUID companyId,String cause,
                                    BigDecimal affectedQuantityKg,String status) implements QualityEvent {
    public static QualityFailureUpdated of(QualityFailure failure) {
        return new QualityFailureUpdated(failure.id(),failure.batchId(),failure.companyId(),failure.rootCause(),
                failure.affectedQtyKg(),failure.status().name());
    }
    @Override public String eventType() { return "QualityFailureUpdated"; }
    @Override public String aggregateId() { return failureId.toString(); }
}

