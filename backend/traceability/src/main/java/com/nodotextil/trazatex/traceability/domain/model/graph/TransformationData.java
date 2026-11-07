package com.nodotextil.trazatex.traceability.domain.model.graph;

import com.nodotextil.trazatex.traceability.domain.model.TransformationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Una transformación tal como está en el grafo, <b>sin filtrar</b> (ver {@link BatchData}). */
public record TransformationData(UUID transformationId, UUID companyId, TransformationType type, Instant startedAt, Instant completedAt,
                                 String machineName, String machineType, BigDecimal shrinkageKg, BigDecimal wasteKg, String wasteReason,
                                 boolean provisional) {

    public boolean isComplete() {
        return !provisional && companyId != null && type != null && startedAt != null && completedAt != null;
    }
}
