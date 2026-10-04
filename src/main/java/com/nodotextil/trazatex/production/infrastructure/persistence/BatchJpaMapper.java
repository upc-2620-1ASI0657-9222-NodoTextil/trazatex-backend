package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.CompositionComponent;

final class BatchJpaMapper {

    private BatchJpaMapper() {
    }

    static BatchJpaEntity toEntity(Batch batch) {
        return new BatchJpaEntity(
                batch.id(),
                batch.traceabilityId(),
                batch.qrCode(),
                batch.responsibleCompanyId(),
                batch.supplierName(),
                batch.geographicOrigin(),
                batch.materialType(),
                batch.quantityKg(),
                batch.composition().stream()
                        .map(component -> new CompositionComponentJpaEmbeddable(
                                component.material(), component.percentage()))
                        .toList(),
                batch.operationalPhase(),
                batch.registeredAt(),
                batch.receptionCharacteristics());
    }

    static Batch toDomain(BatchJpaEntity entity) {
        return Batch.reconstitute(
                entity.getId(),
                entity.getTraceabilityId(),
                entity.getQrCode(),
                entity.getResponsibleCompanyId(),
                entity.getSupplierName(),
                entity.getGeographicOrigin(),
                entity.getMaterialType(),
                entity.getQuantityKg(),
                entity.getComposition().stream()
                        .map(component -> new CompositionComponent(
                                component.getMaterial(), component.getPercentage()))
                        .toList(),
                entity.getOperationalPhase(),
                entity.getRegisteredAt(),
                entity.getReceptionCharacteristics());
    }
}
