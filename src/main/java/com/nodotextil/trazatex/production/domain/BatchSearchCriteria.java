package com.nodotextil.trazatex.production.domain;

import java.time.LocalDateTime;

public record BatchSearchCriteria(
        String traceabilityId,
        MaterialType materialType,
        OperationalPhase operationalPhase,
        LocalDateTime registeredFrom,
        LocalDateTime registeredTo) {

    public BatchSearchCriteria {
        if (traceabilityId != null && traceabilityId.isBlank()) {
            traceabilityId = null;
        }
        if (registeredFrom != null
                && registeredTo != null
                && registeredFrom.isAfter(registeredTo)) {
            throw new InvalidBatchException(
                    "Registration start date cannot be after end date");
        }
    }
}
