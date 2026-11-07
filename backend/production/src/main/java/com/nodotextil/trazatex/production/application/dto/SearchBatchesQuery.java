package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;

import java.time.LocalDate;

/** Filtros de US13. Las fechas son días calendario de Lima, ambos inclusive. {@code page}/{@code size} nulos usan los valores por defecto. */
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
