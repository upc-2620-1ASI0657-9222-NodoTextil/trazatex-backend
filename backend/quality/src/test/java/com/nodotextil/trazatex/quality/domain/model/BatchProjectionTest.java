package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static com.nodotextil.trazatex.quality.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.quality.domain.Fixtures.NOW;
import static com.nodotextil.trazatex.quality.domain.Fixtures.OTHER_COMPANY;
import static com.nodotextil.trazatex.quality.domain.Fixtures.available;
import static com.nodotextil.trazatex.quality.domain.Fixtures.batch;
import static com.nodotextil.trazatex.quality.domain.Fixtures.finished;
import static com.nodotextil.trazatex.quality.domain.Fixtures.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchProjectionTest {

    private static final Instant LATER = NOW.plusSeconds(5);

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    // ---- nacimiento ----

    @Test
    void aRegisteredBatchIsBornAvailableWithoutHoldAndNotReviewed() {
        BatchProjection batch = BatchProjection.registered(id(), COMPANY, NOW);

        assertThat(batch.phase()).isEqualTo(BatchPhase.AVAILABLE);
        assertThat(batch.qualityHold()).isFalse();
        assertThat(batch.qualityStatus()).isEqualTo(QualityStatus.NOT_REVIEWED);
        assertThat(batch.everControlled()).isFalse();
        assertThat(batch.batchVersion()).isEqualTo(BatchProjection.NO_VERSION);
    }

    @Test
    void aSplitChildInheritsTheParentsQualityStatus() {
        BatchProjection child = BatchProjection.splitChild(id(), COMPANY, QualityStatus.CONFORMING, NOW);

        assertThat(child.phase()).isEqualTo(BatchPhase.AVAILABLE);
        assertThat(child.qualityHold()).isFalse();
        assertThat(child.qualityStatus()).isEqualTo(QualityStatus.CONFORMING);
    }

    // ---- BatchPhaseChanged: versiones y filas parciales ----

    @Test
    void aPhaseChangeWithAHigherVersionIsAppliedWithTheCompanyPhaseAndHoldAfterTheChange() {
        BatchProjection batch = BatchProjection.registered(id(), COMPANY, NOW);

        boolean applied = batch.applyPhaseChanged(OTHER_COMPANY, BatchPhase.IN_TRANSFER, true, 4, LATER);

        assertThat(applied).isTrue();
        assertThat(batch.companyId()).isEqualTo(OTHER_COMPANY);
        assertThat(batch.phase()).isEqualTo(BatchPhase.IN_TRANSFER);
        assertThat(batch.qualityHold()).isTrue();
        assertThat(batch.batchVersion()).isEqualTo(4);
        assertThat(batch.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void anOlderOrRepeatedPhaseChangeNeverRollsTheBatchBack() {
        BatchProjection batch = BatchProjection.registered(id(), COMPANY, NOW);
        batch.applyPhaseChanged(COMPANY, BatchPhase.IN_QUALITY_CONTROL, false, 5, NOW);

        assertThat(batch.applyPhaseChanged(COMPANY, BatchPhase.AVAILABLE, true, 4, LATER)).as("más vieja").isFalse();
        assertThat(batch.applyPhaseChanged(COMPANY, BatchPhase.AVAILABLE, true, 5, LATER)).as("repetida").isFalse();

        assertThat(batch.phase()).isEqualTo(BatchPhase.IN_QUALITY_CONTROL);
        assertThat(batch.qualityHold()).isFalse();
        assertThat(batch.batchVersion()).isEqualTo(5);
        assertThat(batch.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void aVersionZeroEventStillAppliesToABatchNoPhaseChangeHasTouched() {
        BatchProjection batch = BatchProjection.registered(id(), COMPANY, NOW);

        assertThat(batch.applyPhaseChanged(COMPANY, BatchPhase.IN_TRANSFORMATION, false, 0, LATER)).isTrue();
        assertThat(batch.phase()).isEqualTo(BatchPhase.IN_TRANSFORMATION);
    }

    @Test
    void aPartialRowIsCompletedByTheBirthEventWithoutTouchingPhaseHoldCompanyOrVersion() {
        BatchProjection batch = BatchProjection.partial(id(), OTHER_COMPANY, BatchPhase.IN_TRANSFER, true, 7, NOW);
        assertThat(batch.qualityStatus()).isNull();

        boolean changed = batch.completeFromCreation(QualityStatus.CONFORMING, LATER);

        assertThat(changed).isTrue();
        assertThat(batch.qualityStatus()).isEqualTo(QualityStatus.CONFORMING);
        assertThat(batch.everControlled()).isTrue();
        assertThat(batch.phase()).isEqualTo(BatchPhase.IN_TRANSFER);
        assertThat(batch.qualityHold()).isTrue();
        assertThat(batch.companyId()).isEqualTo(OTHER_COMPANY);
        assertThat(batch.batchVersion()).isEqualTo(7);
    }

    @Test
    void completingAPartialRowWithANotReviewedBatchDoesNotMarkItAsControlled() {
        BatchProjection batch = BatchProjection.partial(id(), COMPANY, BatchPhase.AVAILABLE, false, 2, NOW);

        batch.completeFromCreation(QualityStatus.NOT_REVIEWED, LATER);

        assertThat(batch.qualityStatus()).isEqualTo(QualityStatus.NOT_REVIEWED);
        assertThat(batch.everControlled()).as("nunca tuvo un control terminado").isFalse();
        assertThat(batch.controlTypeToStart()).isEqualTo(ControlType.EVALUATION);
    }

    @Test
    void aBirthEventNeverOverwritesAQualityStatusQualityAlreadyDecided() {
        BatchProjection batch = available(QualityStatus.FAILED);

        assertThat(batch.completeFromCreation(QualityStatus.NOT_REVIEWED, LATER)).isFalse();

        assertThat(batch.qualityStatus()).isEqualTo(QualityStatus.FAILED);
        assertThat(batch.updatedAt()).isEqualTo(NOW);
    }

    // ---- TransferAccepted ----

    @Test
    void anAcceptedTransferMovesTheBatchToTheTargetCompanyOnlyIfItIsStillWithTheOrigin() {
        BatchProjection batch = BatchProjection.registered(id(), COMPANY, NOW);

        assertThat(batch.applyTransferAccepted(COMPANY, OTHER_COMPANY, LATER)).isEqualTo(BatchProjection.TransferEffect.MOVED);
        assertThat(batch.companyId()).isEqualTo(OTHER_COMPANY);
        assertThat(batch.applyTransferAccepted(COMPANY, OTHER_COMPANY, LATER)).isEqualTo(BatchProjection.TransferEffect.ALREADY_THERE);
        assertThat(batch.applyTransferAccepted(UUID.randomUUID(), UUID.randomUUID(), LATER)).isEqualTo(BatchProjection.TransferEffect.OWNER_MISMATCH);
        assertThat(batch.companyId()).isEqualTo(OTHER_COMPANY);
    }

    // ---- Resultado de un control y decisiones sobre la falla ----

    @Test
    void aConformingControlReleasesTheBatchAndAFailedOneKeepsItOnHold() {
        BatchProjection conforming = available(QualityStatus.NOT_REVIEWED);
        BatchProjection failed = available(QualityStatus.NOT_REVIEWED);

        QualityStatusChange ok = conforming.apply(finished(conforming, TestResult.CONFORMING), LATER);
        QualityStatusChange bad = failed.apply(finished(failed, TestResult.CONFORMING, TestResult.FAILED), LATER);

        assertThat(ok).isEqualTo(new QualityStatusChange(QualityStatus.NOT_REVIEWED, QualityStatus.CONFORMING));
        assertThat(conforming.qualityHold()).isFalse();
        assertThat(bad).isEqualTo(new QualityStatusChange(QualityStatus.NOT_REVIEWED, QualityStatus.FAILED));
        assertThat(failed.qualityHold()).isTrue();
        assertThat(ok.changed() && bad.changed()).isTrue();
    }

    @Test
    void aControlOfAnotherBatchIsNotApplied() {
        BatchProjection one = available(QualityStatus.NOT_REVIEWED);
        BatchProjection other = available(QualityStatus.NOT_REVIEWED);
        ControlOutcome outcome = finished(other, TestResult.CONFORMING);

        assertCode(() -> one.apply(outcome, LATER), ErrorCodes.CONTROL_BATCH_MISMATCH);
        assertThat(one.everControlled()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = QualityStatus.class, names = {"CONFORMING", "FAILED"})
    void aBatchThatCannotHaveAControlRefusesItsResult(QualityStatus status) {
        BatchProjection source = available(QualityStatus.NOT_REVIEWED);
        ControlOutcome outcome = finished(source, TestResult.CONFORMING);
        BatchProjection batch = BatchProjection.restore(source.batchId(), COMPANY, BatchPhase.AVAILABLE, status.retainsBatch(), status, true, 1, NOW);

        assertCode(() -> batch.apply(outcome, LATER), ErrorCodes.INVALID_QUALITY_TRANSITION);
        assertThat(batch.qualityStatus()).isEqualTo(status);
    }

    @Test
    void onlyAFailedBatchGoesToReevaluationKeepingItsHold_US26() {
        BatchProjection failed = available(QualityStatus.FAILED);

        QualityStatusChange change = failed.sendToReevaluation(LATER);

        assertThat(change).isEqualTo(new QualityStatusChange(QualityStatus.FAILED, QualityStatus.PENDING_REEVALUATION));
        assertThat(failed.qualityHold()).isTrue();
        for (QualityStatus status : QualityStatus.values()) {
            if (status != QualityStatus.FAILED) {
                assertCode(() -> available(status).sendToReevaluation(LATER), ErrorCodes.INVALID_QUALITY_TRANSITION);
            }
        }
    }

    @Test
    void onlyAFailedBatchCanBeDiscarded_US27() {
        available(QualityStatus.FAILED).requireFailedToDiscard();
        for (QualityStatus status : QualityStatus.values()) {
            if (status != QualityStatus.FAILED) {
                assertCode(() -> available(status).requireFailedToDiscard(), ErrorCodes.INVALID_QUALITY_TRANSITION);
            }
        }
    }

    @Test
    void theConfirmedDerivedHoldChangesTheQualityOnlyFromNotReviewedOrConforming_US34() {
        for (QualityStatus status : new QualityStatus[]{QualityStatus.NOT_REVIEWED, QualityStatus.CONFORMING}) {
            BatchProjection batch = batch(BatchPhase.AVAILABLE, status, false, false);
            QualityStatusChange change = batch.applyConfirmedDerivedHold(LATER);
            assertThat(change).isEqualTo(new QualityStatusChange(status, QualityStatus.POTENTIAL_DERIVED_FAILURE));
            assertThat(batch.qualityHold()).as("POTENTIAL_DERIVED_FAILURE implica qualityHold = true").isTrue();
        }
        for (QualityStatus status : new QualityStatus[]{QualityStatus.FAILED, QualityStatus.PENDING_REEVALUATION, QualityStatus.POTENTIAL_DERIVED_FAILURE}) {
            BatchProjection batch = available(status);
            assertCode(() -> batch.applyConfirmedDerivedHold(LATER), ErrorCodes.INVALID_QUALITY_TRANSITION);
            assertThat(batch.qualityStatus()).as("conserva su estado").isEqualTo(status);
        }
    }

    @Test
    void operationsOnAPartialRowAreRefusedBecauseItsQualityIsUnknown() {
        BatchProjection partial = BatchProjection.partial(id(), COMPANY, BatchPhase.AVAILABLE, false, 1, NOW);

        assertCode(() -> partial.sendToReevaluation(LATER), ErrorCodes.BATCH_QUALITY_UNKNOWN);
        assertCode(partial::requireFailedToDiscard, ErrorCodes.BATCH_QUALITY_UNKNOWN);
        assertCode(() -> partial.applyConfirmedDerivedHold(LATER), ErrorCodes.BATCH_QUALITY_UNKNOWN);
    }
}
