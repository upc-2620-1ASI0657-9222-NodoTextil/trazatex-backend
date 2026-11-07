package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.production.domain.service.TransformationFactory;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static com.nodotextil.trazatex.production.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.NOW;
import static com.nodotextil.trazatex.production.domain.Fixtures.OPERATOR;
import static com.nodotextil.trazatex.production.domain.Fixtures.OTHER_COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.activeMachine;
import static com.nodotextil.trazatex.production.domain.Fixtures.availableBatch;
import static com.nodotextil.trazatex.production.domain.Fixtures.batch;
import static com.nodotextil.trazatex.production.domain.Fixtures.composition;
import static com.nodotextil.trazatex.production.domain.Fixtures.factory;
import static com.nodotextil.trazatex.production.domain.Fixtures.transformationFactory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US16–US19: inicio, composición, balance, desperdicio y producto final. */
class TransformationTest {

    private final TransformationFactory transformations = transformationFactory();
    private final BatchFactory batchFactory = factory(11);
    private final Machine machine = activeMachine();

    private Transformation start(TransformationType type, Batch... inputs) {
        return transformations.start(UUID.randomUUID(), type, COMPANY, machine, OPERATOR, List.of(inputs), NOW);
    }

    private static BatchFactory.OutputSpec output(String kg) {
        return new BatchFactory.OutputSpec(null, "Hilo", Quantity.ofKg(kg), false, null);
    }

    private static void assertCode(Throwable thrown, String code) {
        assertThat(thrown).isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    // --- inicio (US16) ------------------------------------------------------------------------------------------

    @Test
    void startingRegistersTheStartTimeAndMovesTheBatchesToInTransformation() {
        Batch a = availableBatch("100");
        Batch b = availableBatch("50");

        Transformation transformation = start(TransformationType.SPINNING, a, b);

        assertThat(transformation.status()).isEqualTo(TransformationStatus.IN_PROGRESS);
        assertThat(transformation.startTime()).isEqualTo(NOW);
        assertThat(transformation.endTime()).isNull();
        assertThat(transformation.type()).isEqualTo(TransformationType.SPINNING);
        assertThat(transformation.machineId()).isEqualTo(machine.id());
        assertThat(transformation.operatorId()).isEqualTo(OPERATOR);
        assertThat(a.phase()).isEqualTo(BatchPhase.IN_TRANSFORMATION);
        assertThat(b.phase()).isEqualTo(BatchPhase.IN_TRANSFORMATION);
        assertThat(transformation.inputs()).extracting(TransformationInput::batchId).containsExactly(a.id(), b.id());
        assertThat(transformation.totalInput()).isEqualTo(Quantity.ofKg("150"));
        assertThat(transformation.isMix()).isTrue();
        assertThat(transformation.shrinkage()).isNull();
    }

    @Test
    void oneInputIsNotAMix() {
        assertThat(start(TransformationType.WEAVING, availableBatch("10")).isMix()).isFalse();
    }

    @Test
    void aBatchThatIsNotEnabledPreventsTheStartAndLeavesTheOthersUntouched() {
        Batch ok = availableBatch("100");
        Batch held = batch(BatchPhase.AVAILABLE, true, false, "10");

        assertThatThrownBy(() -> start(TransformationType.SPINNING, ok, held)).isInstanceOf(ConflictException.class);

        assertThat(ok.phase()).as("US16 esc. 2: no se inicia ni se mueve ningún lote").isEqualTo(BatchPhase.AVAILABLE);
    }

    @Test
    void finalProductsAndBatchesInAnotherPhaseCannotEnter() {
        assertThatThrownBy(() -> start(TransformationType.SPINNING, batch(BatchPhase.AVAILABLE, false, true, "10")))
                .satisfies(e -> assertCode(e, ErrorCodes.BATCH_IS_FINAL_PRODUCT));
        assertThatThrownBy(() -> start(TransformationType.SPINNING, batch(BatchPhase.IN_TRANSFER, false, false, "10")))
                .satisfies(e -> assertCode(e, ErrorCodes.INVALID_PHASE_TRANSITION));
        assertThatThrownBy(() -> start(TransformationType.SPINNING, batch(BatchPhase.PROCESSED, false, false, "10")))
                .satisfies(e -> assertCode(e, ErrorCodes.INVALID_PHASE_TRANSITION));
    }

    @Test
    void aMachineThatIsNotActiveCannotBeUsed() {
        Batch batch = availableBatch("10");
        machine.changeStatus(MachineStatus.OUT_OF_SERVICE, OPERATOR, NOW);

        assertThatThrownBy(() -> start(TransformationType.SPINNING, batch)).satisfies(e -> assertCode(e, ErrorCodes.MACHINE_NOT_ACTIVE));
        assertThat(batch.phase()).isEqualTo(BatchPhase.AVAILABLE);

        machine.changeStatus(MachineStatus.INACTIVE, OPERATOR, NOW);
        assertThatThrownBy(() -> start(TransformationType.SPINNING, batch)).satisfies(e -> assertCode(e, ErrorCodes.MACHINE_NOT_ACTIVE));
    }

    @Test
    void theMachineAndTheBatchesMustBelongToTheCompany() {
        Batch own = availableBatch("10");
        Machine foreign = Machine.register(UUID.randomUUID(), OTHER_COMPANY, "X-1", "Ajena", "Cardado", NOW);
        assertThatThrownBy(() -> transformations.start(UUID.randomUUID(), TransformationType.SPINNING, COMPANY, foreign, OPERATOR, List.of(own), NOW))
                .satisfies(e -> assertCode(e, ErrorCodes.MACHINE_COMPANY_MISMATCH));

        assertThatThrownBy(() -> transformations.start(UUID.randomUUID(), TransformationType.SPINNING, OTHER_COMPANY,
                Machine.register(UUID.randomUUID(), OTHER_COMPANY, "X-2", "Propia", "Cardado", NOW), OPERATOR, List.of(own), NOW))
                .satisfies(e -> assertCode(e, ErrorCodes.BATCH_COMPANY_MISMATCH));
        assertThat(own.phase()).isEqualTo(BatchPhase.AVAILABLE);
    }

    @Test
    void needsAtLeastOneBatchAndNoDuplicates() {
        assertThatThrownBy(() -> transformations.start(UUID.randomUUID(), TransformationType.SPINNING, COMPANY, machine, OPERATOR, List.of(), NOW))
                .satisfies(e -> assertCode(e, ErrorCodes.NO_BATCHES_SELECTED));
        Batch batch = availableBatch("10");
        assertThatThrownBy(() -> start(TransformationType.SPINNING, batch, batch)).satisfies(e -> assertCode(e, ErrorCodes.DUPLICATE_BATCH));
        assertThat(batch.phase()).isEqualTo(BatchPhase.AVAILABLE);
    }

    // --- finalización: balance (US18) y desperdicio (US19) -------------------------------------------------------

    @Test
    void finalizingComputesTheShrinkageAndSetsInputsAsProcessedAndOutputsAsAvailable() {
        Batch input = availableBatch("100");
        Transformation transformation = start(TransformationType.SPINNING, input);

        List<Batch> outputs = transformation.finalize(List.of(input), List.of(output("70"), output("10")),
                Quantity.ofKg("5"), "Fibra contaminada", batchFactory, OPERATOR, NOW.plus(Duration.ofHours(2)));

        assertThat(transformation.status()).isEqualTo(TransformationStatus.COMPLETED);
        assertThat(transformation.totalInput()).isEqualTo(Quantity.ofKg("100"));
        assertThat(transformation.totalOutput()).isEqualTo(Quantity.ofKg("80"));
        assertThat(transformation.waste()).isEqualTo(Quantity.ofKg("5"));
        assertThat(transformation.wasteReason()).isEqualTo("Fibra contaminada");
        assertThat(transformation.shrinkage()).as("merma = 100 − 80 − 5").isEqualTo(Quantity.ofKg("15"));
        assertThat(transformation.endTime()).isEqualTo(NOW.plus(Duration.ofHours(2)));
        assertThat(input.phase()).isEqualTo(BatchPhase.PROCESSED);
        assertThat(outputs).hasSize(2).allSatisfy(out -> {
            assertThat(out.phase()).isEqualTo(BatchPhase.AVAILABLE);
            assertThat(out.qualityStatus()).isEqualTo(QualityStatus.NOT_REVIEWED);
            assertThat(out.qualityHold()).isFalse();
            assertThat(out.originType()).isEqualTo(BatchOriginType.TRANSFORMATION);
            assertThat(out.companyId()).isEqualTo(COMPANY);
            assertThat(out.createdBy()).isEqualTo(OPERATOR);
            assertThat(out.traceabilityId()).isNotEqualTo(input.traceabilityId());
            assertThat(out.qrCode()).isNotEqualTo(input.qrCode());
            assertThat(out.isFinalProduct()).isFalse();
        });
        assertThat(outputs.get(0).traceabilityId()).isNotEqualTo(outputs.get(1).traceabilityId());
        assertThat(transformation.outputBatchIds()).containsExactly(outputs.get(0).id(), outputs.get(1).id());
    }

    @Test
    void zeroShrinkageIsValid() {
        Batch input = availableBatch("100");
        Transformation transformation = start(TransformationType.SPINNING, input);

        transformation.finalize(List.of(input), List.of(output("95")), Quantity.ofKg("5"), "Recorte", batchFactory, OPERATOR, NOW);

        assertThat(transformation.shrinkage().isZero()).isTrue();
        assertThat(transformation.status()).isEqualTo(TransformationStatus.COMPLETED);
    }

    @Test
    void anUnbalancedTransformationCannotBeFinalizedAndChangesNothing() {
        Batch input = availableBatch("100");
        Transformation transformation = start(TransformationType.SPINNING, input);

        assertThatThrownBy(() -> transformation.finalize(List.of(input), List.of(output("98")), Quantity.ofKg("3"), "x", batchFactory, OPERATOR, NOW))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertCode(e, ErrorCodes.INVALID_QUANTITIES);
                    assertThat(e.getMessage()).contains("cantidades no son válidas");
                });

        assertThat(transformation.status()).isEqualTo(TransformationStatus.IN_PROGRESS);
        assertThat(transformation.shrinkage()).isNull();
        assertThat(transformation.outputBatchIds()).isEmpty();
        assertThat(input.phase()).as("US18 esc. 2: la entrada sigue en transformación").isEqualTo(BatchPhase.IN_TRANSFORMATION);
    }

    @Test
    void aWasteQuantityRequiresAReason() {
        Batch input = availableBatch("100");
        Transformation transformation = start(TransformationType.SPINNING, input);

        for (String reason : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> transformation.finalize(List.of(input), List.of(output("90")), Quantity.ofKg("1"), reason, batchFactory, OPERATOR, NOW))
                    .satisfies(e -> assertCode(e, ErrorCodes.WASTE_REASON_REQUIRED));
        }
        assertThat(input.phase()).isEqualTo(BatchPhase.IN_TRANSFORMATION);
    }

    @Test
    void withoutWasteNoReasonIsNeededAndNoneIsKept() {
        Batch input = availableBatch("100");
        Transformation transformation = start(TransformationType.SPINNING, input);

        transformation.finalize(List.of(input), List.of(output("90")), null, "ignorado", batchFactory, OPERATOR, NOW);

        assertThat(transformation.waste().isZero()).isTrue();
        assertThat(transformation.wasteReason()).isNull();
        assertThat(transformation.shrinkage()).isEqualTo(Quantity.ofKg("10"));
    }

    @Test
    void aTransformationNeedsAtLeastOneOutput() {
        Batch input = availableBatch("100");
        Transformation transformation = start(TransformationType.SPINNING, input);

        assertThatThrownBy(() -> transformation.finalize(List.of(input), List.of(), null, null, batchFactory, OPERATOR, NOW))
                .satisfies(e -> assertCode(e, ErrorCodes.MISSING_FIELDS));
        assertThatThrownBy(() -> transformation.finalize(List.of(input), null, null, null, batchFactory, OPERATOR, NOW))
                .satisfies(e -> assertCode(e, ErrorCodes.MISSING_FIELDS));
    }

    @Test
    void anOutputNeedsItsMaterialType() {
        Batch input = availableBatch("100");
        Transformation transformation = start(TransformationType.SPINNING, input);
        BatchFactory.OutputSpec noMaterial = new BatchFactory.OutputSpec(null, " ", Quantity.ofKg("90"), false, null);

        assertThatThrownBy(() -> transformation.finalize(List.of(input), List.of(noMaterial), null, null, batchFactory, OPERATOR, NOW))
                .satisfies(e -> assertCode(e, ErrorCodes.MISSING_FIELDS));
        assertThat(input.phase()).as("nada cambia si una salida es inválida").isEqualTo(BatchPhase.IN_TRANSFORMATION);
        assertThat(transformation.status()).isEqualTo(TransformationStatus.IN_PROGRESS);
    }

    @Test
    void aTransformationCanOnlyBeFinalizedOnce() {
        Batch input = availableBatch("100");
        Transformation transformation = start(TransformationType.SPINNING, input);
        transformation.finalize(List.of(input), List.of(output("90")), null, null, batchFactory, OPERATOR, NOW);

        assertThatThrownBy(() -> transformation.finalize(List.of(input), List.of(output("90")), null, null, batchFactory, OPERATOR, NOW))
                .satisfies(e -> assertCode(e, ErrorCodes.TRANSFORMATION_NOT_IN_PROGRESS));
    }

    @Test
    void theInputBatchesMustBeTheOnesThatStarted() {
        Batch input = availableBatch("100");
        Transformation transformation = start(TransformationType.SPINNING, input);
        Batch other = batch(BatchPhase.IN_TRANSFORMATION, false, false, "100");

        assertThatThrownBy(() -> transformation.finalize(List.of(other), List.of(output("90")), null, null, batchFactory, OPERATOR, NOW))
                .satisfies(e -> assertCode(e, ErrorCodes.BATCHES_DO_NOT_MATCH));
        assertThatThrownBy(() -> transformation.finalize(List.of(), List.of(output("90")), null, null, batchFactory, OPERATOR, NOW))
                .satisfies(e -> assertCode(e, ErrorCodes.BATCHES_DO_NOT_MATCH));
        assertThatThrownBy(() -> transformation.finalize(null, List.of(output("90")), null, null, batchFactory, OPERATOR, NOW))
                .satisfies(e -> assertCode(e, ErrorCodes.BATCHES_DO_NOT_MATCH));
    }

    // --- composición (US17) --------------------------------------------------------------------------------------

    @Test
    void oneInputKeepsItsCompositionInEveryOutput() {
        Composition blend = composition("Algodón", "65", "Poliéster", "35");
        Batch input = availableBatch(Quantity.ofKg("100"), blend, new Random(1));
        Transformation transformation = start(TransformationType.SPINNING, input);

        List<Batch> outputs = transformation.finalize(List.of(input), List.of(output("40"), output("40")), null, null, batchFactory, OPERATOR, NOW);

        assertThat(outputs).allSatisfy(out -> assertThat(out.composition()).isEqualTo(blend));
        assertThat(transformation.isMix()).isFalse();
    }

    @Test
    void severalInputsAreMixedByKgWeightedAverage() {
        Random random = new Random(2);
        Batch cotton = availableBatch(Quantity.ofKg("100"), composition("Algodón", "100"), random);
        Batch half = availableBatch(Quantity.ofKg("300"), composition("Algodón", "50", "Poliéster", "50"), random);
        Transformation transformation = start(TransformationType.SPINNING, cotton, half);

        List<Batch> outputs = transformation.finalize(List.of(cotton, half), List.of(output("390")), null, null, batchFactory, OPERATOR, NOW);

        assertThat(transformation.isMix()).isTrue();
        assertThat(outputs.get(0).composition()).isEqualTo(composition("Algodón", "62.5", "Poliéster", "37.5"));
        assertThat(cotton.phase()).isEqualTo(BatchPhase.PROCESSED);
        assertThat(half.phase()).isEqualTo(BatchPhase.PROCESSED);
    }

    // --- producto final (US20) -----------------------------------------------------------------------------------

    @Test
    void anOutputCanBeMarkedAsFinalProductWithCommercialData() {
        Batch input = availableBatch("100");
        Transformation transformation = start(TransformationType.DYEING, input);
        CommercialData data = new CommercialData("Tiendas SAC", new java.math.BigDecimal("999.90"), "PEN", null, "OC-77");

        List<Batch> outputs = transformation.finalize(List.of(input),
                List.of(new BatchFactory.OutputSpec("Tela teñida", "Tela", Quantity.ofKg("90"), true, data), output("5")),
                null, null, batchFactory, OPERATOR, NOW);

        Batch finalProduct = outputs.get(0);
        assertThat(finalProduct.isFinalProduct()).isTrue();
        assertThat(finalProduct.commercialData()).isEqualTo(data);
        assertThat(finalProduct.name()).isEqualTo("Tela teñida");
        assertThat(finalProduct.isEnabled()).as("US20 esc. 4: un producto final ya no opera").isFalse();
        assertThat(outputs.get(1).isFinalProduct()).isFalse();
        assertThat(outputs.get(1).commercialData().isEmpty()).isTrue();
    }

    @Test
    void commercialDataWithoutMarkingFinalProductIsRejected() {
        Batch input = availableBatch("100");
        Transformation transformation = start(TransformationType.DYEING, input);
        CommercialData data = new CommercialData("Tiendas SAC", null, null, null, null);

        assertThatThrownBy(() -> transformation.finalize(List.of(input),
                List.of(new BatchFactory.OutputSpec(null, "Tela", Quantity.ofKg("90"), false, data)),
                null, null, batchFactory, OPERATOR, NOW))
                .satisfies(e -> assertCode(e, ErrorCodes.INVALID_COMMERCIAL_DATA));
        assertThat(input.phase()).isEqualTo(BatchPhase.IN_TRANSFORMATION);
    }

    // --- reconstrucción ------------------------------------------------------------------------------------------

    @Test
    void restoreRebuildsTheTransformationWithTheStrategyOfItsType() {
        TransformationSnapshot snapshot = new TransformationSnapshot(UUID.randomUUID(), COMPANY, TransformationType.WEAVING,
                machine.id(), OPERATOR, TransformationStatus.IN_PROGRESS,
                List.of(new TransformationInput(UUID.randomUUID(), Quantity.ofKg("100"))), List.of(), null, null, null, null,
                null, NOW, null);

        Transformation restored = transformations.restore(snapshot);

        assertThat(restored.type()).isEqualTo(TransformationType.WEAVING);
        assertThat(restored.status()).isEqualTo(TransformationStatus.IN_PROGRESS);
        assertThat(restored.totalInput()).isEqualTo(Quantity.ofKg("100"));
        assertThat(restored.id()).isEqualTo(snapshot.id());
    }
}
