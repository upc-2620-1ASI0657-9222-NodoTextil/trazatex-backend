package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.Transformation;
import com.nodotextil.trazatex.production.domain.model.TransformationStatus;
import com.nodotextil.trazatex.production.domain.model.TransformationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code totalOutputKg}, {@code wasteKg}, {@code shrinkageKg} y {@code endTime} son nulos hasta que se finaliza. */
public record TransformationResult(
        UUID id,
        UUID companyId,
        TransformationType type,
        UUID machineId,
        UUID operatorId,
        TransformationStatus status,
        boolean mix,
        List<Input> inputs,
        BigDecimal totalInputKg,
        BigDecimal totalOutputKg,
        BigDecimal wasteKg,
        String wasteReason,
        BigDecimal shrinkageKg,
        List<UUID> outputBatchIds,
        Instant startTime,
        Instant endTime) {

    public record Input(UUID batchId, BigDecimal quantityKg) {
    }

    public static TransformationResult from(Transformation t) {
        return new TransformationResult(t.id(), t.companyId(), t.type(), t.machineId(), t.operatorId(), t.status(), t.isMix(),
                t.inputs().stream().map(i -> new Input(i.batchId(), i.quantity().kg())).toList(), t.totalInput().kg(),
                t.totalOutput() == null ? null : t.totalOutput().kg(), t.waste() == null ? null : t.waste().kg(),
                t.wasteReason(), t.shrinkage() == null ? null : t.shrinkage().kg(), t.outputBatchIds(), t.startTime(), t.endTime());
    }
}
