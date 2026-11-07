package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.nodotextil.trazatex.quality.domain.Fixtures.NOW;
import static com.nodotextil.trazatex.quality.domain.Fixtures.available;
import static com.nodotextil.trazatex.quality.domain.Fixtures.finished;
import static com.nodotextil.trazatex.quality.domain.Fixtures.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Descartar solo confirma la falla cuando Production confirma {@code DISCARD}. */
class FailureDiscardPendingTest {

    static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000a0ad");

    private static QualityFailure completedFailure() {
        QualityFailure failure = QualityFailure.generate(id(), finished(available(QualityStatus.NOT_REVIEWED), TestResult.FAILED));
        failure.complete("Humedad", new BigDecimal("12.5"), null);
        return failure;
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void aGeneratedFailureCarriesTheCompanyOfTheBatch() {
        BatchProjection batch = available(QualityStatus.NOT_REVIEWED);

        QualityFailure failure = QualityFailure.generate(id(), finished(batch, TestResult.FAILED));

        assertThat(failure.companyId()).isEqualTo(batch.companyId());
    }

    @Test
    void requestingTheDiscardKeepsTheFailureOpenAndFreezesIt() {
        QualityFailure failure = completedFailure();

        assertThat(failure.requestDiscard(ADMIN, NOW)).isTrue();

        assertThat(failure.status()).isEqualTo(FailureStatus.OPEN);
        assertThat(failure.discardPending()).isTrue();
        assertThat(failure.discardRequestedBy()).isEqualTo(ADMIN);
        assertCode(() -> failure.complete("Otra causa", BigDecimal.ONE, null), ErrorCodes.FAILURE_DISCARD_PENDING);
        assertCode(failure::startReevaluation, ErrorCodes.FAILURE_DISCARD_PENDING);
        assertThat(failure.rootCause()).isEqualTo("Humedad");
    }

    @Test
    void askingAgainWhileItIsPendingChangesNothing() {
        QualityFailure failure = completedFailure();
        failure.requestDiscard(ADMIN, NOW);

        assertThat(failure.requestDiscard(UUID.randomUUID(), NOW.plusSeconds(99))).isFalse();

        assertThat(failure.discardRequestedBy()).isEqualTo(ADMIN);
        assertThat(failure.discardRequestedAt()).isEqualTo(NOW);
    }

    @Test
    void theConfirmationOfProductionConfirmsTheFailure() {
        QualityFailure failure = completedFailure();
        failure.requestDiscard(ADMIN, NOW);

        failure.completeDiscard();

        assertThat(failure.status()).isEqualTo(FailureStatus.CONFIRMED);
        assertThat(failure.discardPending()).isFalse();
        assertCode(failure::completeDiscard, ErrorCodes.INVALID_FAILURE_TRANSITION);
    }

    @Test
    void aRejectedDiscardLeavesTheFailureOpenAndEditableAgain() {
        QualityFailure failure = completedFailure();
        failure.requestDiscard(ADMIN, NOW);

        failure.abortDiscard();

        assertThat(failure.status()).isEqualTo(FailureStatus.OPEN);
        assertThat(failure.discardPending()).isFalse();
        failure.complete("Humedad corregida", BigDecimal.TEN, null);
        assertThat(failure.rootCause()).isEqualTo("Humedad corregida");
    }

    @Test
    void confirmingOrAbortingWithoutARequestIsRefused() {
        QualityFailure failure = completedFailure();

        assertCode(failure::completeDiscard, ErrorCodes.INVALID_FAILURE_TRANSITION);
        assertCode(failure::abortDiscard, ErrorCodes.INVALID_FAILURE_TRANSITION);
    }

    @Test
    void theInfoIsCheckedWhenRequestingNotOnlyWhenConfirming() {
        QualityFailure untouched = QualityFailure.generate(id(), finished(available(QualityStatus.NOT_REVIEWED), TestResult.FAILED));

        assertCode(() -> untouched.requestDiscard(ADMIN, NOW), ErrorCodes.FAILURE_INFO_INCOMPLETE);
        assertThat(untouched.discardPending()).isFalse();
        assertCode(() -> completedFailure().requestDiscard(null, NOW), ErrorCodes.MISSING_FIELDS);
    }

    @Test
    void aRestoredFailureKeepsItsPendingDiscard() {
        QualityFailure restored = QualityFailure.restore(id(), id(), id(), UUID.randomUUID(), "Humedad", BigDecimal.ONE, null, FailureStatus.OPEN, NOW, ADMIN);

        assertThat(restored.discardPending()).isTrue();
        restored.completeDiscard();
        assertThat(restored.status()).isEqualTo(FailureStatus.CONFIRMED);
    }
}
