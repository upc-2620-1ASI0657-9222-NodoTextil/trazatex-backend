package com.nodotextil.trazatex.production.domain.event;

import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.Machine;
import com.nodotextil.trazatex.production.domain.model.CompositionComponent;
import com.nodotextil.trazatex.production.domain.model.Transformation;
import com.nodotextil.trazatex.production.domain.model.TransformationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransformationCompleted(
        UUID transformationId,
        TransformationType type,
        UUID companyId,
        UUID machineId,
        String machineName,
        String machineType,
        List<Input> inputs,
        List<Output> outputs,
        BigDecimal wasteKg,
        String wasteReason,
        BigDecimal shrinkageKg,
        Instant startedAt,
        Instant completedAt) implements ProductionEvent {

    public static final String TYPE = "TransformationCompleted";

    public record Input(UUID batchId, BigDecimal quantityKg) {
    }

    public record Output(UUID batchId, String traceabilityId, String qrCode, String materialType, BigDecimal quantityKg,
                         List<CompositionComponent> composition, boolean finalProduct, String name) {
    public Output(UUID batchId, String traceabilityId, String qrCode, String materialType, BigDecimal quantityKg,
                         List<CompositionComponent> composition, boolean finalProduct) {
        this(batchId, traceabilityId, qrCode, materialType, quantityKg, composition, finalProduct, null);
    }

    }

    public static TransformationCompleted of(Transformation transformation, List<Batch> outputs, Machine machine) {
        return new TransformationCompleted(transformation.id(), transformation.type(), transformation.companyId(),
                transformation.machineId(), machine.name(), machine.type(),
                transformation.inputs().stream().map(i -> new Input(i.batchId(), i.quantity().kg())).toList(),
                outputs.stream().map(o -> new Output(o.id(), o.traceabilityId().value(), o.qrCode().value(), o.materialType(),
                        o.quantity().kg(), o.composition().components(), o.isFinalProduct(), o.name())).toList(),
                transformation.waste().kg(), transformation.wasteReason(),
                transformation.shrinkage().kg(), transformation.startTime(), transformation.endTime());
    }

    @Override
    public String eventType() {
        return TYPE;
    }

    @Override
    public String aggregateId() {
        return transformationId.toString();
    }
}
