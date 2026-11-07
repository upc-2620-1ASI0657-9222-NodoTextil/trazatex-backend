package com.nodotextil.trazatex.production.application.port.out;

import com.nodotextil.trazatex.production.domain.model.TransformationStatus;
import com.nodotextil.trazatex.production.domain.model.TransformationType;

import java.util.UUID;

/** Transformaciones de una empresa, de la más reciente a la más antigua. Todo filtro nulo se ignora. */
public record TransformationSearchCriteria(
        UUID companyId,
        TransformationStatus status,
        TransformationType type,
        int page,
        int size) {

    /** {@code size} se limita a {@link Page#MAX_SIZE}: ninguna consulta devuelve más de 100 filas por página. */
    public TransformationSearchCriteria {
        if (page < 0) {
            throw new IllegalArgumentException("page debe ser >= 0: " + page);
        }
        if (size < 1) {
            throw new IllegalArgumentException("size debe ser >= 1: " + size);
        }
        size = Math.min(size, Page.MAX_SIZE);
    }
}
