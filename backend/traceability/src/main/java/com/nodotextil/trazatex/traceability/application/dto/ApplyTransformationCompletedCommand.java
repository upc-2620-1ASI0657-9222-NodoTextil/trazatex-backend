package com.nodotextil.trazatex.traceability.application.dto;

import com.nodotextil.trazatex.traceability.domain.model.CompositionComponent;
import com.nodotextil.trazatex.traceability.domain.model.TransformationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nodotextil.trazatex.traceability.application.dto.EventFields.compositionOf;
import static com.nodotextil.trazatex.traceability.application.dto.EventFields.nonNegativeKg;
import static com.nodotextil.trazatex.traceability.application.dto.EventFields.notEmpty;
import static com.nodotextil.trazatex.traceability.application.dto.EventFields.positiveKg;
import static com.nodotextil.trazatex.traceability.application.dto.EventFields.required;

/**
 * {@code TransformationCompleted}: crea la transformación y las salidas; las entradas ya existen (no cambia su fase, llega con
 * su {@code BatchPhaseChanged}). {@code machineName}, {@code machineType} y {@code wasteReason} son opcionales.
 */
public record ApplyTransformationCompletedCommand(
        UUID eventId,
        UUID transformationId,
        TransformationType type,
        UUID companyId,
        String machineName,
        String machineType,
        List<UUID> inputBatchIds,
        List<Output> outputs,
        BigDecimal wasteKg,
        String wasteReason,
        BigDecimal shrinkageKg,
        Instant startedAt,
        Instant completedAt) {

    public record Output(
            UUID batchId,
            String traceabilityId,
            String qrCode,
            String materialType,
            BigDecimal quantityKg,
            List<CompositionComponent> composition,
            boolean finalProduct, String name) {
    public Output(
            UUID batchId,
            String traceabilityId,
            String qrCode,
            String materialType,
            BigDecimal quantityKg,
            List<CompositionComponent> composition,
            boolean finalProduct) {
        this(batchId, traceabilityId, qrCode, materialType, quantityKg, composition, finalProduct, null);
    }


        public Output {
            required(batchId, "outputs.batchId");
            required(traceabilityId, "outputs.traceabilityId");
            required(qrCode, "outputs.qrCode");
            required(materialType, "outputs.materialType");
            quantityKg = positiveKg(quantityKg, "outputs.quantityKg");
            composition = compositionOf(composition, "outputs.composition");
        }
    }

    public ApplyTransformationCompletedCommand {
        required(eventId, "eventId");
        required(transformationId, "transformationId");
        required(type, "type");
        required(companyId, "companyId");
        inputBatchIds = List.copyOf(notEmpty(inputBatchIds, "inputs"));
        outputs = List.copyOf(required(outputs, "outputs"));
        wasteKg = nonNegativeKg(wasteKg, "wasteKg");
        shrinkageKg = nonNegativeKg(shrinkageKg, "shrinkageKg");
        required(startedAt, "startedAt");
        required(completedAt, "completedAt");
    }
}
