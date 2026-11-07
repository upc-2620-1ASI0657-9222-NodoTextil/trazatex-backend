package com.nodotextil.trazatex.production.application.port.in;

import com.nodotextil.trazatex.production.domain.model.QualityStatus;

import java.util.UUID;

/** {@code QualityStatusChanged}: actualiza la copia de {@code qualityStatus} del lote; no toca fase ni retención. */
public interface ApplyQualityStatusUseCase {

    /**
     * @param companyId la empresa que Quality creía dueña del lote; si ya no lo es (lote transferido) se avisa y se aplica igual
     * @throws com.nodotextil.trazatex.sharedkernel.exception.NotFoundException si el lote no existe
     */
    void apply(UUID batchId, UUID companyId, QualityStatus newStatus, UUID controlId);
}
