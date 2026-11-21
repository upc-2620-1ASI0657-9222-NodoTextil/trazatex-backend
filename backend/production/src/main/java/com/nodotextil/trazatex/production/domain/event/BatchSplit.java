package com.nodotextil.trazatex.production.domain.event;

import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.CompositionComponent;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BatchSplit(UUID parentBatchId, UUID companyId, List<Child> children) implements ProductionEvent {

    public static final String TYPE = "BatchSplit";

    public record Child(UUID batchId, String traceabilityId, String qrCode, String materialType, BigDecimal quantityKg,
                        List<CompositionComponent> composition, String origin, String supplierName, String receptionCharacteristics,
                        QualityStatus qualityStatus, Instant createdAt, String name) {
    public Child(UUID batchId, String traceabilityId, String qrCode, String materialType, BigDecimal quantityKg,
                        List<CompositionComponent> composition, String origin, String supplierName, String receptionCharacteristics,
                        QualityStatus qualityStatus, Instant createdAt) {
        this(batchId, traceabilityId, qrCode, materialType, quantityKg, composition, origin, supplierName, receptionCharacteristics, qualityStatus, createdAt, null);
    }

    }

    public static BatchSplit of(Batch parent, List<Batch> children) {
        return new BatchSplit(parent.id(), parent.companyId(), children.stream()
                .map(c -> new Child(c.id(), c.traceabilityId().value(), c.qrCode().value(), c.materialType(), c.quantity().kg(),
                        c.composition().components(), c.origin(), c.supplierName(), c.receptionCharacteristics(), c.qualityStatus(),
                        c.createdAt(), c.name()))
                .toList());
    }

    @Override
    public String eventType() {
        return TYPE;
    }

    @Override
    public String aggregateId() {
        return parentBatchId.toString();
    }
}
