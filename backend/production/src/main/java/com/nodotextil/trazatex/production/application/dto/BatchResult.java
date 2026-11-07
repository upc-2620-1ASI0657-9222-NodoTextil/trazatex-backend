package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.BatchOriginType;
import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.CommercialData;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Todos los datos de un lote de la propia empresa. */
public record BatchResult(
        UUID id,
        String traceabilityId,
        String qrCode,
        UUID companyId,
        String name,
        String materialType,
        BigDecimal quantityKg,
        List<Component> composition,
        BatchPhase phase,
        QualityStatus qualityStatus,
        boolean qualityHold,
        boolean finalProduct,
        BatchOriginType originType,
        String origin,
        String supplierName,
        String receptionCharacteristics,
        UUID createdBy,
        Instant createdAt,
        Commercial commercialData,
        long version) implements QrView {

    public record Component(String name, BigDecimal percentage) {
    }

    public record Commercial(String buyerOrDistributor, BigDecimal salePrice, String currency,
                             LocalDate commercialExitDate, String commercialReference) {

        static Commercial of(CommercialData data) {
            return new Commercial(data.buyerOrDistributor(), data.salePrice(), data.currency(), data.commercialExitDate(),
                    data.commercialReference());
        }
    }

    public static BatchResult from(Batch batch) {
        return new BatchResult(batch.id(), batch.traceabilityId().value(), batch.qrCode().value(), batch.companyId(),
                batch.name(), batch.materialType(), batch.quantity().kg(),
                batch.composition().components().stream().map(c -> new Component(c.name(), c.percentage())).toList(),
                batch.phase(), batch.qualityStatus(), batch.qualityHold(), batch.isFinalProduct(), batch.originType(),
                batch.origin(), batch.supplierName(), batch.receptionCharacteristics(), batch.createdBy(), batch.createdAt(),
                Commercial.of(batch.commercialData()), batch.version());
    }
}
