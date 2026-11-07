package com.nodotextil.trazatex.quality.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nodotextil.trazatex.quality.domain.Fixtures.available;
import static com.nodotextil.trazatex.quality.domain.Fixtures.finished;
import static com.nodotextil.trazatex.quality.domain.Fixtures.id;
import static org.assertj.core.api.Assertions.assertThat;

/** Qué hace un control finalizado con las fallas: genera, reutiliza (con su historial), resuelve o nada. */
class FailureLifecycleTest {

    @Test
    void anEvaluationThatFailsGeneratesItsOwnFailure_US25() {
        BatchProjection batch = available(QualityStatus.NOT_REVIEWED);
        ControlOutcome outcome = finished(batch, TestResult.FAILED);
        UUID newId = id();

        FailureChange change = FailureLifecycle.after(QualityStatus.NOT_REVIEWED, outcome, null, newId);

        assertThat(change.action()).isEqualTo(FailureAction.GENERATE);
        assertThat(change.failure().id()).isEqualTo(newId);
        assertThat(change.failure().controlId()).isEqualTo(outcome.controlId());
        assertThat(change.failure().status()).isEqualTo(FailureStatus.OPEN);
    }

    @Test
    void aConformingEvaluationLeavesNoFailure() {
        ControlOutcome outcome = finished(available(QualityStatus.NOT_REVIEWED), TestResult.CONFORMING);

        FailureChange change = FailureLifecycle.after(QualityStatus.NOT_REVIEWED, outcome, null, id());

        assertThat(change.action()).isEqualTo(FailureAction.NONE);
        assertThat(change.failure()).isNull();
    }

    @Test
    void aReevaluationThatFailsAgainReusesTheOpenFailureAndPointsItToTheLatestControl_US28() {
        BatchProjection batch = available(QualityStatus.NOT_REVIEWED);
        QualityFailure open = QualityFailure.generate(id(), finished(batch, TestResult.FAILED));
        UUID firstControl = open.controlId();
        ControlOutcome second = finished(available(QualityStatus.PENDING_REEVALUATION), TestResult.FAILED);
        QualityFailure sameBatchFailure = QualityFailure.restore(open.id(), firstControl, second.batchId(), null, null, null, FailureStatus.OPEN);

        FailureChange change = FailureLifecycle.after(QualityStatus.PENDING_REEVALUATION, second, sameBatchFailure, id());

        assertThat(change.action()).isEqualTo(FailureAction.REOPEN);
        assertThat(change.failure()).isSameAs(sameBatchFailure);
        assertThat(change.failure().id()).as("no se crea otra falla").isEqualTo(open.id());
        assertThat(change.failure().controlId()).isEqualTo(second.controlId()).isNotEqualTo(firstControl);
        assertThat(change.failure().status()).isEqualTo(FailureStatus.OPEN);
    }

    @Test
    void aConformingReevaluationResolvesTheOpenFailure_US28() {
        ControlOutcome outcome = finished(available(QualityStatus.PENDING_REEVALUATION), TestResult.CONFORMING);
        QualityFailure open = QualityFailure.restore(id(), id(), outcome.batchId(), "causa", java.math.BigDecimal.ONE, null, FailureStatus.OPEN);

        FailureChange change = FailureLifecycle.after(QualityStatus.PENDING_REEVALUATION, outcome, open, id());

        assertThat(change.action()).isEqualTo(FailureAction.RESOLVE);
        assertThat(open.status()).isEqualTo(FailureStatus.RESOLVED);
    }

    @Test
    void aDerivedFailureReviewThatFailsGeneratesItsOwnFailure_US34Scenario4() {
        ControlOutcome outcome = finished(available(QualityStatus.POTENTIAL_DERIVED_FAILURE), TestResult.FAILED);
        QualityFailure someOtherOpenFailure = QualityFailure.restore(id(), id(), outcome.batchId(), null, null, null, FailureStatus.OPEN);

        FailureChange change = FailureLifecycle.after(QualityStatus.POTENTIAL_DERIVED_FAILURE, outcome, someOtherOpenFailure, id());

        assertThat(change.action()).isEqualTo(FailureAction.GENERATE);
        assertThat(change.failure()).isNotSameAs(someOtherOpenFailure);
        assertThat(someOtherOpenFailure.controlId()).as("la falla ajena no se toca").isNotEqualTo(outcome.controlId());
    }

    @Test
    void aDerivedFailureReviewThatIsConformingHasNoFailureToResolve_US34Scenario3() {
        ControlOutcome outcome = finished(available(QualityStatus.POTENTIAL_DERIVED_FAILURE), TestResult.CONFORMING);

        FailureChange change = FailureLifecycle.after(QualityStatus.POTENTIAL_DERIVED_FAILURE, outcome, null, id());

        assertThat(change.action()).isEqualTo(FailureAction.NONE);
    }

    @Test
    void aReevaluationWithoutAnOpenFailureStillGeneratesOrDoesNothingInsteadOfBreaking() {
        // dato inconsistente (PENDING_REEVALUATION sin falla abierta): el control no se pierde
        assertThat(FailureLifecycle.after(QualityStatus.PENDING_REEVALUATION, finished(available(QualityStatus.PENDING_REEVALUATION), TestResult.FAILED), null, id()).action())
                .isEqualTo(FailureAction.GENERATE);
        assertThat(FailureLifecycle.after(QualityStatus.PENDING_REEVALUATION, finished(available(QualityStatus.PENDING_REEVALUATION), TestResult.CONFORMING), null, id()).action())
                .isEqualTo(FailureAction.NONE);
    }
}
