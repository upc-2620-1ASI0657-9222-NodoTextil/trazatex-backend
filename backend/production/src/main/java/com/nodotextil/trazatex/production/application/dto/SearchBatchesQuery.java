package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;

import java.time.LocalDate;

public record SearchBatchesQuery(
        String traceabilityId,
        String materialType,
        BatchPhase phase,
        QualityStatus qualityStatus,
        LocalDate createdFrom,
        LocalDate createdTo,
        Integer page,
        Integer size) {
}
