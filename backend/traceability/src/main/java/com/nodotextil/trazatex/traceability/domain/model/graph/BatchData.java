package com.nodotextil.trazatex.traceability.domain.model.graph;

import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.CompositionComponent;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BatchData(UUID batchId, String traceabilityId, String qrCode, UUID companyId, String materialType, BatchPhase phase,
                        QualityStatus qualityStatus, Boolean qualityHold, Boolean finalProduct, Instant createdAt, String supplierName,
                        String origin, BigDecimal quantityKg, List<CompositionComponent> composition, String receptionCharacteristics,
                        boolean provisional, String failureCause, List<QualityTestValue> qualityTests, String name) {
    public BatchData(UUID batchId, String traceabilityId, String qrCode, UUID companyId, String materialType, BatchPhase phase,
                        QualityStatus qualityStatus, Boolean qualityHold, Boolean finalProduct, Instant createdAt, String supplierName,
                        String origin, BigDecimal quantityKg, List<CompositionComponent> composition, String receptionCharacteristics,
                        boolean provisional, String failureCause, List<QualityTestValue> qualityTests) {
        this(batchId, traceabilityId, qrCode, companyId, materialType, phase, qualityStatus, qualityHold, finalProduct, createdAt, supplierName, origin, quantityKg, composition, receptionCharacteristics, provisional, failureCause, qualityTests, null);
    }

    public BatchData(UUID batchId,String traceabilityId,String qrCode,UUID companyId,String materialType,BatchPhase phase,
                     QualityStatus qualityStatus,Boolean qualityHold,Boolean finalProduct,Instant createdAt,String supplierName,
                     String origin,BigDecimal quantityKg,List<CompositionComponent> composition,String receptionCharacteristics,boolean provisional) {
        this(batchId,traceabilityId,qrCode,companyId,materialType,phase,qualityStatus,qualityHold,finalProduct,createdAt,supplierName,origin,
                quantityKg,composition,receptionCharacteristics,provisional,null,List.of());
    }

    public boolean isComplete() {
        return !provisional && traceabilityId != null && qrCode != null && materialType != null && companyId != null && phase != null
                && qualityStatus != null && createdAt != null && finalProduct != null;
    }
}
