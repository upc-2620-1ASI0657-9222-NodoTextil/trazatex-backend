package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Lo que ve otra empresa al escanear el QR de un lote ajeno: solo los campos <b>siempre compartidos</b> (docs/domain/
 * traceability.md). Production no conoce la privacidad configurable; la vista completa entre empresas es la genealogía de
 * Traceability. No incluye la identidad del operario ni los datos comerciales (siempre privados).
 */
public record SharedBatchResult(
        UUID batchId,
        String traceabilityId,
        String qrCode,
        UUID companyId,
        String materialType,
        BatchPhase phase,
        QualityStatus qualityStatus,
        Instant createdAt,
        boolean finalProduct) implements QrView {

    public static SharedBatchResult from(Batch batch) {
        return new SharedBatchResult(batch.id(), batch.traceabilityId().value(), batch.qrCode().value(), batch.companyId(),
                batch.materialType(), batch.phase(), batch.qualityStatus(), batch.createdAt(), batch.isFinalProduct());
    }
}
