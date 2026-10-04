package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.MaterialType;
import com.nodotextil.trazatex.production.domain.OperationalPhase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record BatchResponse(
        UUID id,
        String traceabilityId,
        String qrCode,
        UUID responsibleCompanyId,
        String supplierName,
        String geographicOrigin,
        MaterialType materialType,
        BigDecimal quantityKg,
        List<CompositionComponentResponse> composition,
        OperationalPhase operationalPhase,
        LocalDateTime registeredAt,
        String receptionCharacteristics,
        boolean finalProduct,
        String buyerOrDistributor,
        BigDecimal price,
        String currency,
        LocalDate commercialDate,
        String commercialReference) {

    static BatchResponse from(Batch batch) {
        return new BatchResponse(
                batch.id(),
                batch.traceabilityId(),
                batch.qrCode(),
                batch.responsibleCompanyId(),
                batch.supplierName(),
                batch.geographicOrigin(),
                batch.materialType(),
                batch.quantityKg(),
                batch.composition().stream()
                        .map(component -> new CompositionComponentResponse(
                                component.material(), component.percentage()))
                        .toList(),
                batch.operationalPhase(),
                batch.registeredAt(),
                batch.receptionCharacteristics(),
                batch.finalProduct(),
                batch.buyerOrDistributor(),
                batch.price(),
                batch.currency(),
                batch.commercialDate(),
                batch.commercialReference());
    }

    public record CompositionComponentResponse(String material, BigDecimal percentage) {
    }
}
