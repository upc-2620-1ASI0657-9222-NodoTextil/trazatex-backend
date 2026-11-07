package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.nodotextil.trazatex.production.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.OTHER_COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.batch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tabla de transiciones de fase de docs/domain/production.md, comprobada completa: 7 fases × 9 operaciones. */
class BatchTest {

    private enum Operation {
        SPLIT(Batch::markSplit, BatchPhase.AVAILABLE, BatchPhase.SPLIT),
        START_TRANSFORMATION(Batch::startTransformation, BatchPhase.AVAILABLE, BatchPhase.IN_TRANSFORMATION),
        FINISH_TRANSFORMATION(Batch::finishTransformation, BatchPhase.IN_TRANSFORMATION, BatchPhase.PROCESSED),
        START_TRANSFER(Batch::startTransfer, BatchPhase.AVAILABLE, BatchPhase.IN_TRANSFER),
        ACCEPT_TRANSFER(batch -> batch.acceptTransfer(OTHER_COMPANY), BatchPhase.IN_TRANSFER, BatchPhase.AVAILABLE),
        REJECT_TRANSFER(Batch::rejectTransfer, BatchPhase.IN_TRANSFER, BatchPhase.AVAILABLE),
        START_EVALUATION(Batch::startEvaluation, BatchPhase.AVAILABLE, BatchPhase.IN_QUALITY_CONTROL),
        RELEASE(batch -> batch.release(false), BatchPhase.IN_QUALITY_CONTROL, BatchPhase.AVAILABLE),
        DISCARD(Batch::discard, BatchPhase.AVAILABLE, BatchPhase.DISCARDED);

        private final Consumer<Batch> action;
        private final BatchPhase from;
        private final BatchPhase to;

        Operation(Consumer<Batch> action, BatchPhase from, BatchPhase to) {
            this.action = action;
            this.from = from;
            this.to = to;
        }
    }

    static Stream<Arguments> everyPhaseWithEveryOperation() {
        return Stream.of(BatchPhase.values())
                .flatMap(phase -> Stream.of(Operation.values()).map(operation -> Arguments.of(phase, operation)));
    }

    @ParameterizedTest(name = "{0} + {1}")
    @MethodSource("everyPhaseWithEveryOperation")
    void onlyTheDocumentedTransitionsAreAllowed(BatchPhase phase, Operation operation) {
        Batch batch = batch(phase, false, false, "10");

        if (phase == operation.from) {
            operation.action.accept(batch);
            assertThat(batch.phase()).isEqualTo(operation.to);
        } else {
            assertThatThrownBy(() -> operation.action.accept(batch))
                    .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.INVALID_PHASE_TRANSITION));
            assertThat(batch.phase()).as("una transición rechazada no cambia la fase").isEqualTo(phase);
        }
    }

    @ParameterizedTest
    @EnumSource(value = BatchPhase.class, names = {"PROCESSED", "SPLIT", "DISCARDED"})
    void historicalPhasesAreFinal(BatchPhase phase) {
        assertThat(phase.isHistorical()).isTrue();
        Batch batch = batch(phase, false, false, "10");
        for (Operation operation : Operation.values()) {
            assertThatThrownBy(() -> operation.action.accept(batch)).isInstanceOf(ConflictException.class);
        }
        assertThatThrownBy(batch::hold).isInstanceOf(ConflictException.class);
        assertThatThrownBy(batch::releaseHold).isInstanceOf(ConflictException.class);
    }

    @ParameterizedTest
    @EnumSource(value = BatchPhase.class, names = {"AVAILABLE", "IN_TRANSFORMATION", "IN_TRANSFER", "IN_QUALITY_CONTROL"})
    void operativePhasesAreNotHistorical(BatchPhase phase) {
        assertThat(phase.isHistorical()).isFalse();
    }

    // --- habilitado (glosario, RF-024) --------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}, retención={1}, final={2}")
    @MethodSource("everyPhaseAndFlagCombination")
    void aBatchIsEnabledOnlyWhenAvailableWithoutHoldAndNotFinal(BatchPhase phase, boolean hold, boolean finalProduct) {
        Batch batch = batch(phase, hold, finalProduct, "10");
        boolean expected = phase == BatchPhase.AVAILABLE && !hold && !finalProduct;

        assertThat(batch.isEnabled()).isEqualTo(expected);
        if (expected) {
            assertThatCode(batch::ensureEnabled).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(batch::ensureEnabled).isInstanceOf(ConflictException.class);
        }
    }

    static Stream<Arguments> everyPhaseAndFlagCombination() {
        return Stream.of(BatchPhase.values()).flatMap(phase -> Stream.of(false, true)
                .flatMap(hold -> Stream.of(false, true).map(finalProduct -> Arguments.of(phase, hold, finalProduct))));
    }

    @ParameterizedTest
    @EnumSource(value = Operation.class, names = {"SPLIT", "START_TRANSFORMATION", "START_TRANSFER"})
    void aHeldBatchCannotBeSplitTransformedOrTransferred(Operation operation) {
        Batch batch = batch(BatchPhase.AVAILABLE, true, false, "10");

        assertThatThrownBy(() -> operation.action.accept(batch))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.BATCH_ON_QUALITY_HOLD));
        assertThat(batch.phase()).isEqualTo(BatchPhase.AVAILABLE);
    }

    @ParameterizedTest
    @EnumSource(value = Operation.class, names = {"SPLIT", "START_TRANSFORMATION", "START_TRANSFER", "START_EVALUATION"})
    void aFinalProductDoesNotTakePartInNewOperations(Operation operation) {
        Batch batch = batch(BatchPhase.AVAILABLE, false, true, "10");

        assertThatThrownBy(() -> operation.action.accept(batch))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.BATCH_IS_FINAL_PRODUCT));
        assertThat(batch.phase()).isEqualTo(BatchPhase.AVAILABLE);
    }

    // --- retención por calidad (comandos de Quality) -------------------------------------------------------------

    @Test
    void aHeldBatchCanStillStartAQualityEvaluationAndBeDiscarded() {
        Batch toEvaluate = batch(BatchPhase.AVAILABLE, true, false, "10");
        toEvaluate.startEvaluation();
        assertThat(toEvaluate.phase()).isEqualTo(BatchPhase.IN_QUALITY_CONTROL);
        assertThat(toEvaluate.qualityHold()).isTrue();

        Batch toDiscard = batch(BatchPhase.AVAILABLE, true, false, "10");
        toDiscard.discard();
        assertThat(toDiscard.phase()).isEqualTo(BatchPhase.DISCARDED);
    }

    @Test
    void releaseSetsTheHoldInTheSameChange() {
        Batch conforming = batch(BatchPhase.IN_QUALITY_CONTROL, false, false, "10");
        conforming.release(false);
        assertThat(conforming.phase()).isEqualTo(BatchPhase.AVAILABLE);
        assertThat(conforming.qualityHold()).isFalse();
        assertThat(conforming.isEnabled()).isTrue();

        Batch failed = batch(BatchPhase.IN_QUALITY_CONTROL, false, false, "10");
        failed.release(true);
        assertThat(failed.phase()).isEqualTo(BatchPhase.AVAILABLE);
        assertThat(failed.qualityHold()).isTrue();
        assertThat(failed.isEnabled()).isFalse();
    }

    @Test
    void holdAndReleaseHoldOnlyApplyToAnAvailableBatchAndReportWhetherTheyChangedAnything() {
        Batch batch = batch(BatchPhase.AVAILABLE, false, false, "10");

        assertThat(batch.hold()).isTrue();
        assertThat(batch.qualityHold()).isTrue();
        assertThat(batch.hold()).as("ya estaba retenido").isFalse();
        assertThat(batch.releaseHold()).isTrue();
        assertThat(batch.qualityHold()).isFalse();
        assertThat(batch.releaseHold()).as("no estaba retenido").isFalse();

        for (BatchPhase phase : EnumSet.complementOf(EnumSet.of(BatchPhase.AVAILABLE))) {
            Batch other = batch(phase, false, false, "10");
            assertThatThrownBy(other::hold).as("HOLD en %s", phase).isInstanceOf(ConflictException.class);
            assertThatThrownBy(other::releaseHold).as("RELEASE_HOLD en %s", phase).isInstanceOf(ConflictException.class);
        }
    }

    @Test
    void qualityStatusIsAShownCopyAndNeverChangesTheHold() {
        Batch batch = batch(BatchPhase.AVAILABLE, false, false, "10");

        batch.showQualityStatus(QualityStatus.FAILED);

        assertThat(batch.qualityStatus()).isEqualTo(QualityStatus.FAILED);
        assertThat(batch.qualityHold()).isFalse();
        assertThat(batch.isEnabled()).isTrue();
    }

    @Test
    void glossaryRetentionPerQualityStatus() {
        Map<QualityStatus, Boolean> expected = Map.of(
                QualityStatus.NOT_REVIEWED, false,
                QualityStatus.CONFORMING, false,
                QualityStatus.FAILED, true,
                QualityStatus.PENDING_REEVALUATION, true,
                QualityStatus.POTENTIAL_DERIVED_FAILURE, true);
        for (QualityStatus status : QualityStatus.values()) {
            assertThat(status.retainsBatch()).as(status.name()).isEqualTo(expected.get(status));
        }
    }

    // --- transferencia -------------------------------------------------------------------------------------------

    @Test
    void acceptingATransferChangesTheResponsibleCompanyAndKeepsTheCodes() {
        Batch batch = batch(BatchPhase.IN_TRANSFER, false, false, "10");
        TraceabilityId traceabilityId = batch.traceabilityId();
        QrCode qr = batch.qrCode();
        UUID id = batch.id();

        batch.acceptTransfer(OTHER_COMPANY);

        assertThat(batch.companyId()).isEqualTo(OTHER_COMPANY);
        assertThat(batch.phase()).isEqualTo(BatchPhase.AVAILABLE);
        assertThat(batch.traceabilityId()).isEqualTo(traceabilityId);
        assertThat(batch.qrCode()).isEqualTo(qr);
        assertThat(batch.id()).isEqualTo(id);
    }

    @Test
    void rejectingATransferKeepsTheOriginCompany() {
        Batch batch = batch(BatchPhase.IN_TRANSFER, false, false, "10");

        batch.rejectTransfer();

        assertThat(batch.companyId()).isEqualTo(COMPANY);
        assertThat(batch.phase()).isEqualTo(BatchPhase.AVAILABLE);
    }

    @Test
    void aRejectedAcceptanceDoesNotChangeTheCompany() {
        Batch batch = batch(BatchPhase.AVAILABLE, false, false, "10");

        assertThatThrownBy(() -> batch.acceptTransfer(OTHER_COMPANY)).isInstanceOf(ConflictException.class);

        assertThat(batch.companyId()).isEqualTo(COMPANY);
    }

    // --- datos comerciales ---------------------------------------------------------------------------------------

    @Test
    void commercialDataCanOnlyBeEditedOnAFinalProduct() {
        Batch notFinal = batch(BatchPhase.AVAILABLE, false, false, "10");
        assertThatThrownBy(() -> notFinal.updateCommercialData(CommercialData.EMPTY))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.NOT_A_FINAL_PRODUCT));

        Batch finalProduct = batch(BatchPhase.AVAILABLE, false, true, "10");
        CommercialData data = new CommercialData("Tiendas SAC", null, null, null, "REF-1");
        finalProduct.updateCommercialData(data);
        assertThat(finalProduct.commercialData()).isEqualTo(data);
    }

    @Test
    void phasesLabelsAreTheGlossaryNames() {
        Set<String> labels = Set.of("Disponible", "En transformación", "En transferencia", "En control de calidad",
                "Procesado", "Dividido", "Descartado");
        assertThat(Stream.of(BatchPhase.values()).map(BatchPhase::label)).containsExactlyInAnyOrderElementsOf(labels);
    }
}
