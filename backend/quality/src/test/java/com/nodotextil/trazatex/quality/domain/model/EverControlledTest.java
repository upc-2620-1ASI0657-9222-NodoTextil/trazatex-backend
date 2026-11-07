package com.nodotextil.trazatex.quality.domain.model;

import org.junit.jupiter.api.Test;

import static com.nodotextil.trazatex.quality.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.quality.domain.Fixtures.INSPECTOR;
import static com.nodotextil.trazatex.quality.domain.Fixtures.NOW;
import static com.nodotextil.trazatex.quality.domain.Fixtures.available;
import static com.nodotextil.trazatex.quality.domain.Fixtures.finished;
import static com.nodotextil.trazatex.quality.domain.Fixtures.id;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code everControlled} pasa a {@code true} <b>solo</b> cuando un control termina ({@code FINISHED}): ni iniciarlo ni cancelarlo lo
 * tocan. De eso depende que un lote {@code POTENTIAL_DERIVED_FAILURE} reciba una evaluación o una reevaluación (US34).
 */
class EverControlledTest {

    @Test
    void aStartedControlDoesNotMarkTheBatchAsControlled() {
        BatchProjection batch = available(QualityStatus.NOT_REVIEWED);

        QualityControl control = QualityControl.start(id(), batch, INSPECTOR, null, NOW);
        assertThat(batch.everControlled()).as("recién creado (STARTING)").isFalse();
        control.confirmStarted();
        assertThat(batch.everControlled()).as("en curso (IN_PROGRESS)").isFalse();
        control.addTest(id(), "c", "1", "1", "u", TestResult.CONFORMING, null);
        assertThat(batch.everControlled()).as("con pruebas, sin finalizar").isFalse();
    }

    @Test
    void aCancelledControlDoesNotMarkTheBatchAsControlled() {
        BatchProjection batch = available(QualityStatus.NOT_REVIEWED);
        QualityControl.start(id(), batch, INSPECTOR, null, NOW).cancel("INVALID_PHASE_TRANSITION");

        assertThat(batch.everControlled()).isFalse();
        // y como nunca tuvo un control, una revisión por falla derivada sigue siendo una Evaluación (US34 esc. 1)
        batch.applyConfirmedDerivedHold(NOW);
        assertThat(batch.qualityStatus()).isEqualTo(QualityStatus.POTENTIAL_DERIVED_FAILURE);
        assertThat(batch.controlTypeToStart()).isEqualTo(ControlType.EVALUATION);
    }

    @Test
    void aFinishedControlMarksTheBatchAsControlledWhateverItsResult() {
        for (TestResult result : TestResult.values()) {
            BatchProjection batch = available(QualityStatus.NOT_REVIEWED);
            ControlOutcome outcome = finished(batch, result);
            assertThat(batch.everControlled()).as("el control terminó pero aún no se aplicó").isFalse();

            batch.apply(outcome, NOW.plusSeconds(120));

            assertThat(batch.everControlled()).as("resultado %s", result).isTrue();
        }
    }

    @Test
    void afterAFinishedControlADerivedFailureReviewIsAReevaluation() {
        BatchProjection batch = available(QualityStatus.NOT_REVIEWED);
        batch.apply(finished(batch, TestResult.CONFORMING), NOW);
        assertThat(batch.qualityStatus()).isEqualTo(QualityStatus.CONFORMING);

        batch.applyConfirmedDerivedHold(NOW);

        assertThat(batch.qualityStatus()).isEqualTo(QualityStatus.POTENTIAL_DERIVED_FAILURE);
        assertThat(batch.controlTypeToStart()).isEqualTo(ControlType.REEVALUATION);
    }

    @Test
    void aBatchAlreadyConformingWhenItIsBornCountsAsControlled() {
        BatchProjection child = BatchProjection.splitChild(id(), COMPANY, QualityStatus.CONFORMING, NOW);
        BatchProjection other = BatchProjection.splitChild(id(), COMPANY, QualityStatus.NOT_REVIEWED, NOW);

        assertThat(child.everControlled()).isTrue();
        assertThat(other.everControlled()).isFalse();
    }
}
