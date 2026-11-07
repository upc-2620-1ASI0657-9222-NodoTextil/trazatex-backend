package com.nodotextil.trazatex.traceability.application.dto;

import com.nodotextil.trazatex.traceability.domain.model.CompositionComponent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nodotextil.trazatex.traceability.application.dto.EventFields.compositionOf;
import static com.nodotextil.trazatex.traceability.application.dto.EventFields.positiveKg;
import static com.nodotextil.trazatex.traceability.application.dto.EventFields.required;

/** {@code BatchRegistered}. {@code origin}, {@code supplierName} y {@code receptionCharacteristics} son opcionales. */
public record RegisterBatchCommand(
        UUID eventId,
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
        Instant createdAt, String name) {
    public RegisterBatchCommand(
        UUID eventId,
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
        this(eventId, batchId, traceabilityId, qrCode, companyId, materialType, composition, quantityKg, origin, supplierName, receptionCharacteristics, createdAt, null);
    }


    public RegisterBatchCommand {
        required(eventId, "eventId");
        required(batchId, "batchId");
        required(traceabilityId, "traceabilityId");
        required(qrCode, "qrCode");
        required(companyId, "companyId");
        required(materialType, "materialType");
        composition = compositionOf(composition, "composition");
        quantityKg = positiveKg(quantityKg, "quantityKg");
        required(createdAt, "createdAt");
    }
}
