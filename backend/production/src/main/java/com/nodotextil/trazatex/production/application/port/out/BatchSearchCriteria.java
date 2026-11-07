package com.nodotextil.trazatex.production.application.port.out;

import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Búsqueda de lotes de una empresa (US13), del más reciente al más antiguo. Todo filtro nulo se ignora.
 * {@code traceabilityIdPrefix} llega ya normalizado (mayúsculas, sin confusiones O/0 I/L/1); {@code materialType} se
 * compara sin distinguir mayúsculas; el rango es {@code [createdFrom, createdTo)}.
 */
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

    /** {@code size} se limita a {@link Page#MAX_SIZE}: ninguna consulta devuelve más de 100 filas por página. */
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
