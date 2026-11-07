package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.nodotextil.trazatex.quality.domain.Fixtures.NOW;
import static com.nodotextil.trazatex.quality.domain.Fixtures.available;
import static com.nodotextil.trazatex.quality.domain.Fixtures.finished;
import static com.nodotextil.trazatex.quality.domain.Fixtures.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US25 esc. 2, US26, US27 y US28: la falla y su ciclo de vida. */
class QualityFailureTest {

    private static ControlOutcome failedOutcome(BatchProjection batch) {
        return finished(batch, TestResult.FAILED);
    }

    private static QualityFailure openFailure() {
        return QualityFailure.generate(id(), failedOutcome(available(QualityStatus.NOT_REVIEWED)));
    }

    private static final java.util.UUID USER = java.util.UUID.fromString("00000000-0000-0000-0000-00000000a0ad");

    /** Descartar de principio a fin: se pide y Production confirma. */
    private static void discard(QualityFailure failure) {
        failure.requestDiscard(USER, NOW);
        failure.completeDiscard();
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void aFailedControlGeneratesAnOpenFailureLinkedToTheBatchAndTheControl() {
        BatchProjection batch = available(QualityStatus.NOT_REVIEWED);
        ControlOutcome outcome = failedOutcome(batch);

        QualityFailure failure = QualityFailure.generate(id(), outcome);

        assertThat(failure.status()).isEqualTo(FailureStatus.OPEN);
        assertThat(failure.batchId()).isEqualTo(batch.batchId());
        assertThat(failure.controlId()).isEqualTo(outcome.controlId());
        assertThat(failure.rootCause()).isNull();
        assertThat(failure.affectedQtyKg()).isNull();
    }

    @Test
    void aConformingControlDoesNotGenerateAFailure() {
        ControlOutcome conforming = finished(available(QualityStatus.NOT_REVIEWED), TestResult.CONFORMING);

        assertCode(() -> QualityFailure.generate(id(), conforming), ErrorCodes.INVALID_FAILURE_TRANSITION);
    }

    @Test
    void causeQuantityAndObservationsAreKept_US26Scenario1() {
        QualityFailure failure = openFailure();

        failure.complete(" Humedad excesiva ", new BigDecimal("12.500"), " lote húmedo ");

        assertThat(failure.rootCause()).isEqualTo("Humedad excesiva");
        assertThat(failure.affectedQtyKg()).isEqualByComparingTo("12.5");
        assertThat(failure.observations()).isEqualTo("lote húmedo");
        assertThat(failure.status()).isEqualTo(FailureStatus.OPEN);
        failure.complete("Humedad", new BigDecimal("3"), null);
        assertThat(failure.observations()).isNull();
    }

    @Test
    void theCauseAndAPositiveQuantityAreRequired() {
        QualityFailure failure = openFailure();

        assertCode(() -> failure.complete(null, BigDecimal.ONE, null), ErrorCodes.MISSING_FIELDS);
        assertCode(() -> failure.complete("  ", BigDecimal.ONE, null), ErrorCodes.MISSING_FIELDS);
        assertCode(() -> failure.complete("causa", null, null), ErrorCodes.MISSING_FIELDS);
        assertCode(() -> failure.complete("causa", BigDecimal.ZERO, null), ErrorCodes.INVALID_QUANTITY);
        assertCode(() -> failure.complete("causa", new BigDecimal("-1"), null), ErrorCodes.INVALID_QUANTITY);
        assertThat(failure.rootCause()).as("nada se guardó a medias").isNull();
    }

    @Test
    void confirmingTheFailureIsFinal_US27() {
        QualityFailure failure = openFailure();
        failure.complete("causa", BigDecimal.TEN, null);

        discard(failure);

        assertThat(failure.status()).isEqualTo(FailureStatus.CONFIRMED);
        assertCode(() -> failure.requestDiscard(USER, NOW), ErrorCodes.INVALID_FAILURE_TRANSITION);
        assertCode(failure::startReevaluation, ErrorCodes.INVALID_FAILURE_TRANSITION);
        assertCode(() -> failure.complete("otra", BigDecimal.ONE, null), ErrorCodes.INVALID_FAILURE_TRANSITION);
    }

    @Test
    void sendingTheFailureToReevaluationKeepsItOpen_US26Scenario2() {
        QualityFailure failure = openFailure();

        failure.startReevaluation();   // sin causa ni cantidad: enviar a reevaluación no exige nada

        assertThat(failure.status()).isEqualTo(FailureStatus.OPEN);
        assertThat(failure.rootCause()).isNull();
    }

    @Test
    void confirmingNeedsTheCauseAndAPositiveQuantity_FailureInfoIncomplete() {
        QualityFailure untouched = openFailure();
        QualityFailure completed = openFailure();
        completed.complete("Humedad", BigDecimal.ONE, null);
        QualityFailure blankCause = QualityFailure.restore(id(), id(), id(), "  ", BigDecimal.ONE, null, FailureStatus.OPEN);
        QualityFailure zeroQuantity = QualityFailure.restore(id(), id(), id(), "Humedad", BigDecimal.ZERO, null, FailureStatus.OPEN);
        QualityFailure noQuantity = QualityFailure.restore(id(), id(), id(), "Humedad", null, null, FailureStatus.OPEN);

        for (QualityFailure incomplete : new QualityFailure[]{untouched, blankCause, zeroQuantity, noQuantity}) {
            assertCode(() -> incomplete.requestDiscard(USER, NOW), ErrorCodes.FAILURE_INFO_INCOMPLETE);
            assertThat(incomplete.status()).as("sigue abierta").isEqualTo(FailureStatus.OPEN);
        }
        discard(completed);
        assertThat(completed.status()).isEqualTo(FailureStatus.CONFIRMED);
    }

    @Test
    void anAlreadyConfirmedFailureReportsTheStateErrorBeforeTheMissingInfo() {
        QualityFailure confirmed = QualityFailure.restore(id(), id(), id(), null, null, null, FailureStatus.CONFIRMED);

        assertCode(() -> confirmed.requestDiscard(USER, NOW), ErrorCodes.INVALID_FAILURE_TRANSITION);
    }

    @Test
    void aConformingReevaluationResolvesTheFailure_US28Scenario1() {
        BatchProjection batch = available(QualityStatus.PENDING_REEVALUATION);
        QualityFailure failure = QualityFailure.generate(id(), failedOutcome(batch));

        failure.resolve(finished(batch, TestResult.CONFORMING));

        assertThat(failure.status()).isEqualTo(FailureStatus.RESOLVED);
        assertCode(() -> failure.requestDiscard(USER, NOW), ErrorCodes.INVALID_FAILURE_TRANSITION);
    }

    @Test
    void aFailedReevaluationReopensTheSameFailureAndPointsItToTheLatestControl_US28Scenario2() {
        BatchProjection batch = available(QualityStatus.PENDING_REEVALUATION);
        QualityFailure failure = QualityFailure.generate(id(), failedOutcome(batch));
        java.util.UUID firstControl = failure.controlId();
        ControlOutcome second = failedOutcome(batch);

        failure.reopenAfterFailedReevaluation(second);

        assertThat(failure.status()).isEqualTo(FailureStatus.OPEN);
        assertThat(failure.controlId()).isEqualTo(second.controlId()).isNotEqualTo(firstControl);
    }

    @Test
    void aFailureOnlyAcceptsTheResultOfAControlOfItsOwnBatch() {
        QualityFailure failure = openFailure();
        ControlOutcome otherBatchFailed = failedOutcome(available(QualityStatus.NOT_REVIEWED));
        ControlOutcome otherBatchOk = finished(available(QualityStatus.NOT_REVIEWED), TestResult.CONFORMING);

        assertCode(() -> failure.reopenAfterFailedReevaluation(otherBatchFailed), ErrorCodes.CONTROL_BATCH_MISMATCH);
        assertCode(() -> failure.resolve(otherBatchOk), ErrorCodes.CONTROL_BATCH_MISMATCH);
    }

    @Test
    void theResultMustMatchWhatTheTransitionExpects() {
        BatchProjection batch = available(QualityStatus.PENDING_REEVALUATION);
        QualityFailure failure = QualityFailure.generate(id(), failedOutcome(batch));

        assertCode(() -> failure.resolve(failedOutcome(batch)), ErrorCodes.INVALID_FAILURE_TRANSITION);
        assertCode(() -> failure.reopenAfterFailedReevaluation(finished(batch, TestResult.CONFORMING)), ErrorCodes.INVALID_FAILURE_TRANSITION);
        assertThat(failure.status()).isEqualTo(FailureStatus.OPEN);
    }

}
