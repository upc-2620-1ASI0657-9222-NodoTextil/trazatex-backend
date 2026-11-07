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

/**
 * Tópico {@code production.transformation}, clave transformationId. Lo consumen traceability, quality y analytics: las
 * salidas no llevan {@code BatchRegistered}, así que este evento las da a conocer (nacen {@code AVAILABLE} y
 * {@code NOT_REVIEWED}). Los datos comerciales de un producto final son privados y no viajan. {@code wasteReason} es nulo
 * cuando no hubo desperdicio (Traceability lo trata como campo configurable {@code WASTE_REASON}).
 * {@code machineName} y {@code machineType} son una foto de la máquina al finalizar (renombrarla después no reescribe la
 * historia) y son lo que ve otra empresa como {@code MACHINERY}: {@code machineId} es un UUID que no puede consultar. El código
 * interno de la máquina no viaja. {@code outputs[].name} (agregado en V2) es el nombre libre de cada salida (si no se da, su
 * Traceability ID): solo para la empresa dueña, como en {@code BatchRegistered}.
 */
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

    /** Nombre del evento: el componente {@code type} es el tipo de transformación, por eso no se llama {@code type()}. */
    @Override
    public String eventType() {
        return TYPE;
    }

    @Override
    public String aggregateId() {
        return transformationId.toString();
    }
}
