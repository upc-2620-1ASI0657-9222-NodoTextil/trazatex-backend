package com.nodotextil.trazatex.quality.domain.event;

import com.nodotextil.trazatex.quality.domain.model.QualityFailure;
import java.math.BigDecimal;
import java.util.UUID;

public record QualityFailureUpdated(UUID failureId,UUID batchId,UUID companyId,String cause,
                                    BigDecimal affectedQuantityKg,String status) implements QualityEvent {
    public static QualityFailureUpdated of(QualityFailure failure) {
        return new QualityFailureUpdated(failure.id(),failure.batchId(),failure.companyId(),failure.rootCause(),
                failure.affectedQtyKg(),failure.status().name());
    }
    @Override public String eventType() { return "QualityFailureUpdated"; }
    @Override public String aggregateId() { return failureId.toString(); }
}
