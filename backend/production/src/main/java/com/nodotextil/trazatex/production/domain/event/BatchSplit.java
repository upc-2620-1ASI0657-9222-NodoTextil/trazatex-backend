package com.nodotextil.trazatex.production.domain.event;

import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.CompositionComponent;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tópico {@code production.batch}, clave parentBatchId. Lo consumen traceability, quality y analytics: los hijos no llevan
 * {@code BatchRegistered}, así que este evento es el que los da a conocer (nacen {@code AVAILABLE} y con el
 * {@code qualityStatus} del padre).
 */
public record BatchSplit(UUID parentBatchId, UUID companyId, List<Child> children) implements ProductionEvent {

    public static final String TYPE = "BatchSplit";

    /**
     * Un hijo nace con los datos del padre (material, composición, origen, proveedor y recepción; decisión de 04a) y los hijos
     * no llevan {@code BatchRegistered}: sin copiarlos aquí los consumidores no los conocerían. {@code createdAt} es el instante
     * de la división. {@code finalProduct} no viaja: un hijo nunca es producto final. {@code name} (agregado en V2) es el nombre libre
     * del hijo (si no se da, el del padre): solo para la empresa dueña, como en {@code BatchRegistered}.
     */
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
