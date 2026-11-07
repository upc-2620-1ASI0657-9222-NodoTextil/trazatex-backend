package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static com.nodotextil.trazatex.quality.domain.Fixtures.NOW;
import static com.nodotextil.trazatex.quality.domain.Fixtures.available;
import static com.nodotextil.trazatex.quality.domain.Fixtures.id;
import static com.nodotextil.trazatex.quality.domain.Fixtures.startedControl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US24 (pruebas), US25 y US28 (resultado del control). */
class ControlFinishTest {

    private static QualityControl control() {
        return startedControl(available(QualityStatus.NOT_REVIEWED));
    }

    @Test
    void aTestIsKeptAndLinkedToItsControl_US24() {
        QualityControl control = control();

        QualityTest test = control.addTest(id(), " Resistencia a la tracción ", "50", "52,3", "N", TestResult.CONFORMING, " dentro de rango ");

        assertThat(control.tests()).containsExactly(test);
        assertThat(test.controlId()).isEqualTo(control.id());
        assertThat(test.criterionName()).isEqualTo("Resistencia a la tracción");
        assertThat(test.expectedValue()).isEqualTo("50");
        assertThat(test.obtainedValue()).isEqualTo("52,3");
        assertThat(test.unit()).isEqualTo("N");
        assertThat(test.testResult()).isEqualTo(TestResult.CONFORMING);
        assertThat(test.remarks()).isEqualTo("dentro de rango");
    }

    @Test
    void remarksAreOptional() {
        QualityTest test = control().addTest(id(), "Humedad", "8", "7", "%", TestResult.CONFORMING, "   ");

        assertThat(test.remarks()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void everyOtherTestFieldIsRequired(String blank) {
        QualityControl control = control();
        UUID id = id();

        assertThatThrownBy(() -> control.addTest(id, blank, "1", "1", "u", TestResult.CONFORMING, null)).isInstanceOf(TrazatexException.class)
                .satisfies(e -> assertThat(((TrazatexException) e).code()).isEqualTo(ErrorCodes.MISSING_FIELDS));
        assertThatThrownBy(() -> control.addTest(id, "c", blank, "1", "u", TestResult.CONFORMING, null)).isInstanceOf(TrazatexException.class);
        assertThatThrownBy(() -> control.addTest(id, "c", "1", blank, "u", TestResult.CONFORMING, null)).isInstanceOf(TrazatexException.class);
        assertThatThrownBy(() -> control.addTest(id, "c", "1", "1", blank, TestResult.CONFORMING, null)).isInstanceOf(TrazatexException.class);
        assertThat(control.tests()).as("una prueba incompleta no se conserva").isEmpty();
    }

    @Test
    void aTestNeedsItsResult() {
        assertThatThrownBy(() -> control().addTest(id(), "c", "1", "1", "u", null, null)).isInstanceOfSatisfying(TrazatexException.class,
                e -> {
                    assertThat(e.code()).isEqualTo(ErrorCodes.MISSING_FIELDS);
                    assertThat(e.details()).containsExactly("testResult");
                });
    }

    @Test
    void allConformingTestsGiveAConformingBatchWithoutHold_US25Scenario1() {
        QualityControl control = control();
        control.addTest(id(), "a", "1", "1", "u", TestResult.CONFORMING, null);
        control.addTest(id(), "b", "2", "2", "u", TestResult.CONFORMING, null);

        ControlOutcome outcome = control.finish(NOW.plusSeconds(90));

        assertThat(control.status()).isEqualTo(ControlStatus.FINISHED);
        assertThat(control.overallResult()).isEqualTo(TestResult.CONFORMING);
        assertThat(control.endDate()).isEqualTo(NOW.plusSeconds(90));
        assertThat(outcome.overallResult()).isEqualTo(TestResult.CONFORMING);
        assertThat(outcome.newQualityStatus()).isEqualTo(QualityStatus.CONFORMING);
        assertThat(outcome.qualityHold()).isFalse();
        assertThat(outcome.failedTestIds()).isEmpty();
        assertThat(outcome.startedAt()).isEqualTo(NOW);
        assertThat(outcome.finishedAt()).isEqualTo(NOW.plusSeconds(90));
        assertThat(outcome.controlId()).isEqualTo(control.id());
        assertThat(outcome.controlType()).isEqualTo(ControlType.EVALUATION);
    }

    @Test
    void oneFailedTestGivesAFailedBatchThatStaysOnHold_US25Scenario2() {
        QualityControl control = control();
        control.addTest(id(), "a", "1", "1", "u", TestResult.CONFORMING, null);
        UUID failing = control.addTest(id(), "b", "2", "9", "u", TestResult.FAILED, "fuera de rango").id();
        control.addTest(id(), "c", "3", "3", "u", TestResult.CONFORMING, null);

        ControlOutcome outcome = control.finish(NOW.plusSeconds(10));

        assertThat(control.overallResult()).isEqualTo(TestResult.FAILED);
        assertThat(outcome.newQualityStatus()).isEqualTo(QualityStatus.FAILED);
        assertThat(outcome.qualityHold()).isTrue();
        assertThat(outcome.failedTestIds()).isEqualTo(List.of(failing));
    }

    @Test
    void theOutcomeCanOnlyComeFromFinishingAControl() {
        assertThat(ControlOutcome.class.getConstructors()).as("sin constructor público").isEmpty();
    }
}
