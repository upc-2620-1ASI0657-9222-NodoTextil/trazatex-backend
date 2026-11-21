package com.nodotextil.trazatex.production.application.port.in;

import com.nodotextil.trazatex.production.domain.model.QualityStatus;

import java.util.UUID;

public interface ApplyQualityStatusUseCase {

    void apply(UUID batchId, UUID companyId, QualityStatus newStatus, UUID controlId);
}
