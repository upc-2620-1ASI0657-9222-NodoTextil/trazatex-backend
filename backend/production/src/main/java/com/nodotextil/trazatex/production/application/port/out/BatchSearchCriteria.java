package com.nodotextil.trazatex.production.application.port.out;

import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;

import java.time.Instant;
import java.util.UUID;

public record BatchSearchCriteria(
        UUID companyId,
        String traceabilityIdPrefix,
        String materialType,
        BatchPhase phase,
        QualityStatus qualityStatus,
        Instant createdFrom,
        Instant createdTo,
        int page,
        int size) {

    public BatchSearchCriteria {
        if (page < 0) {
            throw new IllegalArgumentException("page debe ser >= 0: " + page);
        }
        if (size < 1) {
            throw new IllegalArgumentException("size debe ser >= 1: " + size);
        }
        size = Math.min(size, Page.MAX_SIZE);
    }
}
