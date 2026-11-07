package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.nodotextil.trazatex.quality.domain.Fixtures.INSPECTOR;
import static com.nodotextil.trazatex.quality.domain.Fixtures.NOW;
import static com.nodotextil.trazatex.quality.domain.Fixtures.available;
import static com.nodotextil.trazatex.quality.domain.Fixtures.id;
import static com.nodotextil.trazatex.quality.domain.Fixtures.startedControl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Finalizar solo cambia el estado del control cuando Production confirma {@code RELEASE}: hasta entonces la solicitud queda pendiente. */
class ControlFinishPendingTest {

    static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000a0ad");

    private static QualityControl controlWithTests(TestResult... results) {
        QualityControl control = startedControl(available(QualityStatus.NOT_REVIEWED));
        for (TestResult result : results) {
            control.addTest(id(), "Criterio", "100", "98", "%", result, null);
        }
        return control;
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void requestingTheFinishFreezesTheResultButDoesNotFinishTheControl() {
        QualityControl control = controlWithTests(TestResult.CONFORMING, TestResult.FAILED);

        boolean requested = control.requestFinish(ADMIN, NOW.plusSeconds(60));

        assertThat(requested).isTrue();
        assertThat(control.status()).as("sigue en curso hasta la confirmación").isEqualTo(ControlStatus.IN_PROGRESS);
        assertThat(control.finishPending()).isTrue();
        assertThat(control.finishResult()).isEqualTo(TestResult.FAILED);
        assertThat(control.finishRequestedBy()).isEqualTo(ADMIN);
        assertThat(control.finishRequestedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(control.overallResult()).as("el resultado oficial aún no existe").isNull();
        assertThat(control.endDate()).isNull();
    }

    @Test
    void whileTheFinishIsPendingTheTestsAreFrozen() {
        QualityControl control = controlWithTests(TestResult.CONFORMING);
        control.requestFinish(ADMIN, NOW);

        assertCode(() -> control.addTest(id(), "Otra", "1", "1", "u", TestResult.FAILED, null), ErrorCodes.CONTROL_FINISH_PENDING);

        assertThat(control.tests()).hasSize(1);
        assertThat(control.finishResult()).isEqualTo(TestResult.CONFORMING);
    }

    @Test
    void askingAgainWhileItIsPendingChangesNothing() {
        QualityControl control = controlWithTests(TestResult.FAILED);
        control.requestFinish(ADMIN, NOW);

        boolean again = control.requestFinish(UUID.randomUUID(), NOW.plusSeconds(500));

        assertThat(again).isFalse();
        assertThat(control.finishRequestedBy()).isEqualTo(ADMIN);
        assertThat(control.finishRequestedAt()).isEqualTo(NOW);
    }

    @Test
    void confirmingFinishesTheControlWithTheFrozenResultAndTheUsersFinishInstant() {
        QualityControl control = controlWithTests(TestResult.CONFORMING, TestResult.FAILED);
        control.requestFinish(ADMIN, NOW.plusSeconds(60));

        ControlOutcome outcome = control.completeFinish();

        assertThat(control.status()).isEqualTo(ControlStatus.FINISHED);
        assertThat(control.overallResult()).isEqualTo(TestResult.FAILED);
        assertThat(control.endDate()).as("la fecha de fin es la del usuario, no la de la confirmación").isEqualTo(NOW.plusSeconds(60));
        assertThat(outcome.finishedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(outcome.newQualityStatus()).isEqualTo(QualityStatus.FAILED);
        assertThat(outcome.qualityHold()).isTrue();
        assertThat(control.finishPending()).as("ya no está pendiente: terminó").isFalse();
    }

    @Test
    void aRejectedReleaseLeavesTheControlInProgressAndTheTestsOpenAgain() {
        QualityControl control = controlWithTests(TestResult.FAILED);
        control.requestFinish(ADMIN, NOW);

        control.abortFinish();

        assertThat(control.status()).isEqualTo(ControlStatus.IN_PROGRESS);
        assertThat(control.finishPending()).isFalse();
        assertThat(control.finishResult()).isNull();
        assertThat(control.finishRequestedBy()).isNull();
        control.addTest(id(), "Corrección", "1", "1", "u", TestResult.CONFORMING, null);
        assertThat(control.tests()).hasSize(2);
        assertThat(control.requestFinish(ADMIN, NOW.plusSeconds(5))).as("se puede volver a pedir").isTrue();
        assertThat(control.finishResult()).isEqualTo(TestResult.FAILED);
    }

    @Test
    void confirmingOrAbortingWithoutAPendingFinishIsRefused() {
        QualityControl control = controlWithTests(TestResult.CONFORMING);

        assertCode(control::completeFinish, ErrorCodes.CONTROL_NOT_IN_PROGRESS);
        assertCode(control::abortFinish, ErrorCodes.CONTROL_NOT_IN_PROGRESS);
    }

    @Test
    void theFinishCanOnlyBeRequestedFromInProgressAndNeedsTestsAndWhoFinishes() {
        QualityControl starting = QualityControl.start(id(), available(QualityStatus.NOT_REVIEWED), INSPECTOR, null, NOW);
        QualityControl noTests = controlWithTests();
        QualityControl cancelled = QualityControl.start(id(), available(QualityStatus.NOT_REVIEWED), INSPECTOR, null, NOW);
        cancelled.cancel("COMPANY_INACTIVE");
        QualityControl finished = controlWithTests(TestResult.CONFORMING);
        finished.finish(NOW);

        assertCode(() -> starting.requestFinish(ADMIN, NOW), ErrorCodes.CONTROL_NOT_IN_PROGRESS);
        assertCode(() -> cancelled.requestFinish(ADMIN, NOW), ErrorCodes.CONTROL_NOT_IN_PROGRESS);
        assertCode(() -> finished.requestFinish(ADMIN, NOW), ErrorCodes.CONTROL_NOT_IN_PROGRESS);
        assertCode(() -> noTests.requestFinish(ADMIN, NOW), ErrorCodes.CONTROL_HAS_NO_TESTS);
        assertCode(() -> controlWithTests(TestResult.CONFORMING).requestFinish(null, NOW), ErrorCodes.MISSING_FIELDS);
        assertThat(noTests.finishPending()).isFalse();
    }

    @Test
    void aRestoredPendingControlKeepsItsFrozenResult() {
        UUID id = id();
        Instant requested = NOW.plusSeconds(30);

        QualityControl restored = QualityControl.restore(id, id(), UUID.randomUUID(), ControlType.EVALUATION, ControlStatus.IN_PROGRESS, null, INSPECTOR, NOW,
                null, null, null, requested, ADMIN, TestResult.FAILED, java.util.List.of());

        assertThat(restored.finishPending()).isTrue();
        assertThat(restored.finishResult()).isEqualTo(TestResult.FAILED);
        assertThat(restored.completeFinish().overallResult()).isEqualTo(TestResult.FAILED);
    }
}
