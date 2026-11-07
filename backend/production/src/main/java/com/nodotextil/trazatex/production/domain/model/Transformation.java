package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Transformación textil (US16–US19, ADR-003). Se inicia con uno o más lotes habilitados y una maquinaria activa
 * (más de un lote es una mezcla) y se finaliza con su balance de masa:
 *
 * <pre>merma = entrada total − salida total − desperdicio  (nunca se ingresa; si es negativa, 400)</pre>
 *
 * <p><b>La invariante vive en {@link #finalize}</b> y se comprueba antes de consultar a la
 * {@link TransformationStrategy} del tipo, que solo puede agregar restricciones. Nada cambia si algo falla: primero
 * se valida todo y recién después se modifican los lotes.
 *
 * <p>Se crea con {@code TransformationFactory} (que elige la estrategia del tipo); la estrategia no se persiste.
 */
public final class Transformation {

    private final UUID id;
    private final UUID companyId;
    private final TransformationType type;
    private final UUID machineId;
    private final UUID operatorId;
    private final TransformationStrategy strategy;
    private TransformationStatus status;
    private final List<TransformationInput> inputs;
    private List<UUID> outputBatchIds;
    private Quantity totalInput;
    private Quantity totalOutput;
    private Quantity waste;
    private String wasteReason;
    private Quantity shrinkage;
    private final Instant startTime;
    private Instant endTime;

    private Transformation(TransformationStrategy strategy, TransformationSnapshot s) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.id = Objects.requireNonNull(s.id(), "id");
        this.companyId = Objects.requireNonNull(s.companyId(), "companyId");
        this.type = Objects.requireNonNull(s.type(), "type");
        this.machineId = Objects.requireNonNull(s.machineId(), "machineId");
        this.operatorId = s.operatorId();
        this.status = s.status();
        this.inputs = List.copyOf(s.inputs());
        this.outputBatchIds = s.outputBatchIds() == null ? List.of() : List.copyOf(s.outputBatchIds());
        this.totalInput = s.totalInput();
        this.totalOutput = s.totalOutput();
        this.waste = s.waste();
        this.wasteReason = s.wasteReason();
        this.shrinkage = s.shrinkage();
        this.startTime = s.startTime();
        this.endTime = s.endTime();
        if (strategy.type() != type) {
            throw new IllegalArgumentException("La estrategia " + strategy.type() + " no es la del tipo " + type);
        }
    }

    /**
     * Inicia una transformación (US16). Los lotes deben ser de la empresa, estar habilitados y no repetirse; la
     * maquinaria, de la empresa y {@code ACTIVE}. Registra la hora de inicio y pasa los lotes a {@code IN_TRANSFORMATION}.
     */
    public static Transformation start(UUID id, TransformationStrategy strategy, UUID companyId, Machine machine,
                                       UUID operatorId, List<Batch> batches, Instant now) {
        if (batches == null || batches.isEmpty()) {
            throw new ValidationException(ErrorCodes.NO_BATCHES_SELECTED,
                    "Seleccione al menos un lote para la transformación.", null);
        }
        Set<UUID> seen = new HashSet<>();
        for (Batch batch : batches) {
            if (!seen.add(batch.id())) {
                throw new ValidationException(ErrorCodes.DUPLICATE_BATCH,
                        "El lote " + batch.traceabilityId() + " está repetido en la selección.", null);
            }
        }
        machine.ensureUsableBy(companyId);
        for (Batch batch : batches) {
            requireCompany(batch, companyId);
            batch.ensureEnabled();
        }
        List<TransformationInput> inputs = new ArrayList<>();
        for (Batch batch : batches) {
            inputs.add(new TransformationInput(batch.id(), batch.quantity()));
            batch.startTransformation();
        }
        return new Transformation(strategy, new TransformationSnapshot(id, companyId, strategy.type(), machine.id(),
                operatorId, TransformationStatus.IN_PROGRESS, inputs, List.of(), null, null, null, null, null, now, null));
    }

    public static Transformation restore(TransformationStrategy strategy, TransformationSnapshot snapshot) {
        return new Transformation(strategy, snapshot);
    }

    /**
     * Finaliza la transformación (US18, US19, US17). {@code inputBatches} son los lotes de entrada que se iniciaron.
     *
     * @return los lotes de salida nuevos ({@code AVAILABLE}, {@code NOT_REVIEWED}, con Traceability ID y QR nuevos);
     * los de entrada quedan {@code PROCESSED}
     */
    public List<Batch> finalize(List<Batch> inputBatches, List<BatchFactory.OutputSpec> outputSpecs, Quantity waste,
                                String wasteReason, BatchFactory factory, UUID completedBy, Instant now) {
        if (status != TransformationStatus.IN_PROGRESS) {
            throw new ConflictException(ErrorCodes.TRANSFORMATION_NOT_IN_PROGRESS, "La transformación ya fue finalizada.");
        }
        if (outputSpecs == null || outputSpecs.isEmpty()) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS,
                    "Faltan datos obligatorios: outputs.", List.of("outputs"));
        }
        ensureSameBatchesAsStarted(inputBatches);
        for (Batch batch : inputBatches) {
            batch.ensurePhase(BatchPhase.IN_TRANSFORMATION);
        }

        Quantity declaredWaste = waste == null ? Quantity.ZERO : waste;
        if (!declaredWaste.isZero() && (wasteReason == null || wasteReason.isBlank())) {
            throw new ValidationException(ErrorCodes.WASTE_REASON_REQUIRED,
                    "Indique el motivo del desperdicio.", null);
        }

        Quantity input = Quantity.ZERO;
        for (TransformationInput entry : inputs) {
            input = input.plus(entry.quantity());
        }
        Quantity output = Quantity.ZERO;
        for (BatchFactory.OutputSpec spec : outputSpecs) {
            output = output.plus(spec.quantity());
        }

        // Invariante de balance (ADR-003): merma = entrada − salida − desperdicio ≥ 0. Ninguna estrategia la relaja.
        BigDecimal shrinkageKg = input.minus(output).subtract(declaredWaste.kg());
        if (shrinkageKg.signum() < 0) {
            throw new ValidationException(ErrorCodes.INVALID_QUANTITIES,
                    "Las cantidades no son válidas: la salida total (" + output + ") más el desperdicio ("
                            + declaredWaste + ") supera la entrada total (" + input + ").", null);
        }
        Quantity computedShrinkage = Quantity.nonNegative(shrinkageKg);
        strategy.validate(new BalanceSummary(input, output, declaredWaste, computedShrinkage, inputs.size(), outputSpecs.size()));

        // Composición (US17): la misma si hay una entrada; promedio ponderado por kg si hay varias (mezcla)
        List<Composition.Contribution> contributions = new ArrayList<>();
        for (Batch batch : inputBatches) {
            contributions.add(new Composition.Contribution(batch.composition(), batch.quantity()));
        }
        Composition composition = Composition.weightedAverage(contributions);

        List<Batch> outputs = new ArrayList<>();
        for (BatchFactory.OutputSpec spec : outputSpecs) {
            outputs.add(factory.output(spec, composition, companyId, completedBy, now));
        }

        // Todo validado: recién ahora se modifica
        for (Batch batch : inputBatches) {
            batch.finishTransformation();
        }
        this.totalInput = input;
        this.totalOutput = output;
        this.waste = declaredWaste;
        this.wasteReason = declaredWaste.isZero() ? null : wasteReason.trim();
        this.shrinkage = computedShrinkage;
        this.outputBatchIds = outputs.stream().map(Batch::id).toList();
        this.endTime = now;
        this.status = TransformationStatus.COMPLETED;
        return List.copyOf(outputs);
    }

    /** Una entrada con más de una fuente es una mezcla (glosario): no es una operación aparte. */
    public boolean isMix() {
        return inputs.size() > 1;
    }

    private void ensureSameBatchesAsStarted(List<Batch> inputBatches) {
        Set<UUID> expected = new HashSet<>();
        inputs.forEach(entry -> expected.add(entry.batchId()));
        Set<UUID> given = new HashSet<>();
        if (inputBatches != null) {
            inputBatches.forEach(batch -> given.add(batch.id()));
        }
        if (!expected.equals(given) || inputBatches.size() != inputs.size()) {
            throw new ConflictException(ErrorCodes.BATCHES_DO_NOT_MATCH,
                    "Los lotes dados no son los de entrada de esta transformación.");
        }
    }

    private static void requireCompany(Batch batch, UUID companyId) {
        if (!batch.companyId().equals(companyId)) {
            throw new ConflictException(ErrorCodes.BATCH_COMPANY_MISMATCH,
                    "El lote " + batch.traceabilityId() + " no pertenece a la empresa.");
        }
    }

    public UUID id() {
        return id;
    }

    public UUID companyId() {
        return companyId;
    }

    public TransformationType type() {
        return type;
    }

    public UUID machineId() {
        return machineId;
    }

    public UUID operatorId() {
        return operatorId;
    }

    public TransformationStatus status() {
        return status;
    }

    public List<TransformationInput> inputs() {
        return inputs;
    }

    /** Vacía hasta que se finaliza. */
    public List<UUID> outputBatchIds() {
        return outputBatchIds;
    }

    /** Entrada total; se conoce desde el inicio. */
    public Quantity totalInput() {
        return totalInput != null ? totalInput : inputs.stream().map(TransformationInput::quantity).reduce(Quantity.ZERO, Quantity::plus);
    }

    /** {@code null} hasta que se finaliza. */
    public Quantity totalOutput() {
        return totalOutput;
    }

    /** {@code null} hasta que se finaliza. */
    public Quantity waste() {
        return waste;
    }

    public String wasteReason() {
        return wasteReason;
    }

    /** {@code null} hasta que se finaliza. */
    public Quantity shrinkage() {
        return shrinkage;
    }

    public Instant startTime() {
        return startTime;
    }

    /** {@code null} hasta que se finaliza. */
    public Instant endTime() {
        return endTime;
    }
}
