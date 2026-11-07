package com.nodotextil.trazatex.traceability.domain.model.view;

import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;

import java.time.Instant;
import java.util.UUID;

/** Un resultado de búsqueda: solo los campos siempre compartidos y la empresa responsable (un lote ajeno y uno propio se ven igual). */
public record VisibleBatchSummary(UUID batchId, String traceabilityId, String qrCode, UUID companyId, String businessName, String materialType,
                                  BatchPhase phase, QualityStatus qualityStatus, Instant createdAt, boolean finalProduct, Access access)
        implements VisibleView {
}
