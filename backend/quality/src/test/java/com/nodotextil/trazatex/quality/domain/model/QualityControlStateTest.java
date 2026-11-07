package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.nodotextil.trazatex.quality.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.quality.domain.Fixtures.INSPECTOR;
import static com.nodotextil.trazatex.quality.domain.Fixtures.NOW;
import static com.nodotextil.trazatex.quality.domain.Fixtures.available;
import static com.nodotextil.trazatex.quality.domain.Fixtures.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El patrón State del control: cada operación en cada estado, sin excepciones olvidadas. */
class QualityControlStateTest {

    enum Operation {
        CONFIRM_STARTED(c -> c.confirmStarted()),
        CANCEL(c -> c.cancel("INVALID_PHASE_TRANSITION")),
        ADD_TEST(c -> c.addTest(id(), "Resistencia", "50", "52", "N", TestResult.CONFORMING, null)),
        FINISH(c -> c.finish(NOW.plusSeconds(1)));

        final Consumer<QualityControl> run;

        Operation(Consumer<QualityControl> run) {
            this.run = run;
        }
    }

    /** Un control en el estado pedido, llevado hasta ahí solo con operaciones públicas. */
    static QualityControl controlIn(ControlStatus status) {
        QualityControl control = QualityControl.start(id(), available(QualityStatus.NOT_REVIEWED), INSPECTOR, null, NOW);
        switch (status) {
            case STARTING -> { }
            case IN_PROGRESS -> control.confirmStarted();
            case FINISHED -> {
                control.confirmStarted();
                control.addTest(id(), "Resistencia", "50", "52", "N", TestResult.CONFORMING, null);
                control.finish(NOW.plusSeconds(1));
            }
            case CANCELLED -> control.cancel("BATCH_IS_FINAL_PRODUCT");
        }
        assertThat(control.status()).isEqualTo(status);
        return control;
    }

    /** Estado al que pasa cada operación, o el código del 409 si ese estado no la admite. */
    static Stream<org.junit.jupiter.params.provider.Arguments> everyOperationInEveryState() {
        record Case(ControlStatus from, Operation operation, ControlStatus to, String errorCode) {
        }
        List<Case> cases = List.of(
                new Case(ControlStatus.STARTING, Operation.CONFIRM_STARTED, ControlStatus.IN_PROGRESS, null),
                new Case(ControlStatus.STARTING, Operation.CANCEL, ControlStatus.CANCELLED, null),
                new Case(ControlStatus.STARTING, Operation.ADD_TEST, null, ErrorCodes.CONTROL_NOT_IN_PROGRESS),
                new Case(ControlStatus.STARTING, Operation.FINISH, null, ErrorCodes.CONTROL_NOT_IN_PROGRESS),

                new Case(ControlStatus.IN_PROGRESS, Operation.CONFIRM_STARTED, null, ErrorCodes.INVALID_CONTROL_TRANSITION),
                new Case(ControlStatus.IN_PROGRESS, Operation.CANCEL, null, ErrorCodes.INVALID_CONTROL_TRANSITION),
                new Case(ControlStatus.IN_PROGRESS, Operation.ADD_TEST, ControlStatus.IN_PROGRESS, null),
                // sin pruebas no se puede finalizar (su propio caso abajo); con la prueba que ADD_TEST deja sí
                new Case(ControlStatus.FINISHED, Operation.CONFIRM_STARTED, null, ErrorCodes.INVALID_CONTROL_TRANSITION),
                new Case(ControlStatus.FINISHED, Operation.CANCEL, null, ErrorCodes.INVALID_CONTROL_TRANSITION),
                new Case(ControlStatus.FINISHED, Operation.ADD_TEST, null, ErrorCodes.CONTROL_NOT_IN_PROGRESS),
                new Case(ControlStatus.FINISHED, Operation.FINISH, null, ErrorCodes.CONTROL_NOT_IN_PROGRESS),

                new Case(ControlStatus.CANCELLED, Operation.CONFIRM_STARTED, null, ErrorCodes.INVALID_CONTROL_TRANSITION),
                new Case(ControlStatus.CANCELLED, Operation.CANCEL, null, ErrorCodes.INVALID_CONTROL_TRANSITION),
                new Case(ControlStatus.CANCELLED, Operation.ADD_TEST, null, ErrorCodes.CONTROL_NOT_IN_PROGRESS),
                new Case(ControlStatus.CANCELLED, Operation.FINISH, null, ErrorCodes.CONTROL_NOT_IN_PROGRESS));
        return cases.stream().map(c -> org.junit.jupiter.params.provider.Arguments.of(c.from(), c.operation(), c.to(), c.errorCode()));
    }

    @Test
    void theTableCoversEveryOperationInEveryStateExceptFinishingAControlInProgress() {
        // 4 estados × 4 operaciones = 16; falta solo IN_PROGRESS + FINISH, que depende de si hay pruebas (se prueba aparte)
        assertThat(everyOperationInEveryState().count()).isEqualTo(ControlStatus.values().length * Operation.values().length - 1);
    }

    @ParameterizedTest(name = "{0} + {1} → {2} {3}")
    @MethodSource("everyOperationInEveryState")
    void everyOperationEitherMovesToTheNextStateOrIsRejectedWithItsOwnCode(ControlStatus from, Operation operation, ControlStatus to, String errorCode) {
        QualityControl control = controlIn(from);

        if (errorCode == null) {
            operation.run.accept(control);
            assertThat(control.status()).isEqualTo(to);
        } else {
            assertThatThrownBy(() -> operation.run.accept(control)).isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(errorCode));
            assertThat(control.status()).as("un rechazo no cambia el estado").isEqualTo(from);
        }
    }

    @Test
    void aControlStartsStartingWithItsStartDateAndNoEndDateOrResult() {
        QualityControl control = QualityControl.start(id(), available(QualityStatus.NOT_REVIEWED), INSPECTOR, "  revisar humedad ", NOW);

        assertThat(control.status()).isEqualTo(ControlStatus.STARTING);
        assertThat(control.startDate()).isEqualTo(NOW);
        assertThat(control.endDate()).isNull();
        assertThat(control.overallResult()).isNull();
        assertThat(control.internalObservations()).isEqualTo("revisar humedad");
        assertThat(control.status().active()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ControlStatus.class)
    void onlyStartingAndInProgressAreActive(ControlStatus status) {
        assertThat(status.active()).isEqualTo(status == ControlStatus.STARTING || status == ControlStatus.IN_PROGRESS);
    }

    @Test
    void finishingWithoutTestsIsRejectedAndTheControlStaysInProgress() {
        QualityControl control = controlIn(ControlStatus.IN_PROGRESS);

        assertThatThrownBy(() -> control.finish(NOW.plusSeconds(1)))
                .isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.CONTROL_HAS_NO_TESTS));

        assertThat(control.status()).isEqualTo(ControlStatus.IN_PROGRESS);
        assertThat(control.endDate()).isNull();
        assertThat(control.overallResult()).isNull();
    }

    @Test
    void aFinishedControlKeepsItsEndDateAndResultAndRefusesFurtherTests() {
        QualityControl control = controlIn(ControlStatus.FINISHED);

        assertThat(control.endDate()).isEqualTo(NOW.plusSeconds(1));
        assertThat(control.overallResult()).isEqualTo(TestResult.CONFORMING);
        assertThat(control.tests()).hasSize(1);
        assertThatThrownBy(() -> control.tests().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aCancelledControlNeverGetsAnEndDateOrAResultAndKeepsTheReasonProductionGave() {
        QualityControl control = controlIn(ControlStatus.CANCELLED);

        assertThat(control.endDate()).isNull();
        assertThat(control.overallResult()).isNull();
        assertThat(control.cancellationReason()).isEqualTo("BATCH_IS_FINAL_PRODUCT");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"  "})
    void cancellingNeedsAReasonAndWithoutOneTheControlStaysStarting(String blank) {
        QualityControl control = controlIn(ControlStatus.STARTING);

        assertThatThrownBy(() -> control.cancel(blank))
                .isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.MISSING_FIELDS));

        assertThat(control.status()).isEqualTo(ControlStatus.STARTING);
        assertThat(control.cancellationReason()).isNull();
    }

    @Test
    void aControlThatIsNotCancelledHasNoReason() {
        for (ControlStatus status : new ControlStatus[]{ControlStatus.STARTING, ControlStatus.IN_PROGRESS, ControlStatus.FINISHED}) {
            assertThat(controlIn(status).cancellationReason()).as(status.name()).isNull();
        }
    }

    @Test
    void aRestoredControlKeepsItsStateAndBehavesAccordingToIt() {
        UUID id = id();
        QualityControl restored = QualityControl.restore(id, id(), COMPANY, ControlType.EVALUATION, ControlStatus.STARTING, null, INSPECTOR, NOW, null, null,
                null, List.of());

        assertThat(restored.status()).isEqualTo(ControlStatus.STARTING);
        restored.confirmStarted();
        assertThat(restored.status()).isEqualTo(ControlStatus.IN_PROGRESS);
        QualityControl cancelled = QualityControl.restore(id(), id(), COMPANY, ControlType.REEVALUATION, ControlStatus.CANCELLED, null, INSPECTOR, NOW, null,
                null, "COMPANY_INACTIVE", List.of());
        assertThat(cancelled.cancellationReason()).isEqualTo("COMPANY_INACTIVE");
        assertThatThrownBy(() -> cancelled.cancel("OTRO")).isInstanceOf(TrazatexException.class);
    }

    @Test
    void aControlNeedsItsBatchAndItsInspector() {
        assertThatThrownBy(() -> QualityControl.start(id(), null, INSPECTOR, null, NOW))
                .isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.MISSING_FIELDS));
        assertThatThrownBy(() -> QualityControl.start(id(), available(QualityStatus.NOT_REVIEWED), (UUID) null, null, NOW))
                .isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.MISSING_FIELDS));
    }
}
