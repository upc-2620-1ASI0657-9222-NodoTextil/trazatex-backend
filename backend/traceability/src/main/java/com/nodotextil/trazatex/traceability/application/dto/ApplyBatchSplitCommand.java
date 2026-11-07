package com.nodotextil.trazatex.traceability.application.dto;

import com.nodotextil.trazatex.traceability.domain.model.CompositionComponent;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nodotextil.trazatex.traceability.application.dto.EventFields.compositionOf;
import static com.nodotextil.trazatex.traceability.application.dto.EventFields.notEmpty;
import static com.nodotextil.trazatex.traceability.application.dto.EventFields.positiveKg;
import static com.nodotextil.trazatex.traceability.application.dto.EventFields.required;

/** {@code BatchSplit}: el padre ya existe (no cambia su fase, llega con su {@code BatchPhaseChanged}) y los hijos nacen aquí. */
public record ApplyBatchSplitCommand(UUID eventId, UUID parentBatchId, UUID companyId, List<Child> children) {

    public record Child(
            UUID batchId,
            String traceabilityId,
            String qrCode,
            String materialType,
            BigDecimal quantityKg,
            List<CompositionComponent> composition,
            String origin,
            String supplierName,
            String receptionCharacteristics,
            QualityStatus qualityStatus,
            Instant createdAt, String name) {
    public Child(
            UUID batchId,
            String traceabilityId,
            String qrCode,
            String materialType,
            BigDecimal quantityKg,
            List<CompositionComponent> composition,
            String origin,
            String supplierName,
            String receptionCharacteristics,
            QualityStatus qualityStatus,
            Instant createdAt) {
        this(batchId, traceabilityId, qrCode, materialType, quantityKg, composition, origin, supplierName, receptionCharacteristics, qualityStatus, createdAt, null);
    }


        public Child {
            required(batchId, "children.batchId");
            required(traceabilityId, "children.traceabilityId");
            required(qrCode, "children.qrCode");
            required(materialType, "children.materialType");
            quantityKg = positiveKg(quantityKg, "children.quantityKg");
            composition = compositionOf(composition, "children.composition");
            required(qualityStatus, "children.qualityStatus");
            required(createdAt, "children.createdAt");
        }
    }

    public ApplyBatchSplitCommand {
        required(eventId, "eventId");
        required(parentBatchId, "parentBatchId");
        required(companyId, "companyId");
        children = List.copyOf(notEmpty(children, "children"));
    }
}
