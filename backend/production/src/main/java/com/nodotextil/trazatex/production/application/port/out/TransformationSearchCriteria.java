package com.nodotextil.trazatex.production.application.port.out;

import com.nodotextil.trazatex.production.domain.model.TransformationStatus;
import com.nodotextil.trazatex.production.domain.model.TransformationType;

import java.util.UUID;

public record TransformationSearchCriteria(
        UUID companyId,
        TransformationStatus status,
        TransformationType type,
        int page,
        int size) {

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
