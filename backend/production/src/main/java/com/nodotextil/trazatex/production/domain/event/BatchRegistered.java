package com.nodotextil.trazatex.production.domain.event;

import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.CompositionComponent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tópico {@code production.batch}, clave batchId. Lo consumen traceability, quality y analytics. Solo lo que el catálogo
 * define: ni quién lo registró ni datos comerciales. {@code name} (agregado en V2) es el nombre libre del lote: opcional (nulo en
 * eventos anteriores) y solo para la empresa dueña; Traceability no lo muestra en una vista {@code SHARED} (communication.md).
 */
public record BatchRegistered(
        UUID batchId,
        String traceabilityId,
        String qrCode,
        UUID companyId,
        String materialType,
        List<CompositionComponent> composition,
        BigDecimal quantityKg,
        String origin,
        String supplierName,
        String receptionCharacteristics,
        Instant createdAt, String name) implements ProductionEvent {
    public BatchRegistered(
        UUID batchId,
        String traceabilityId,
        String qrCode,
        UUID companyId,
        String materialType,
        List<CompositionComponent> composition,
        BigDecimal quantityKg,
        String origin,
        String supplierName,
        String receptionCharacteristics,
        Instant createdAt) {
        this(batchId, traceabilityId, qrCode, companyId, materialType, composition, quantityKg, origin, supplierName, receptionCharacteristics, createdAt, null);
    }


    public static final String TYPE = "BatchRegistered";

    public static BatchRegistered of(Batch batch) {
        return new BatchRegistered(batch.id(), batch.traceabilityId().value(), batch.qrCode().value(), batch.companyId(),
                batch.materialType(), batch.composition().components(), batch.quantity().kg(), batch.origin(),
                batch.supplierName(), batch.receptionCharacteristics(), batch.createdAt(), batch.name());
    }

    @Override
    public String eventType() {
        return TYPE;
    }

    @Override
    public String aggregateId() {
        return batchId.toString();
    }
}
