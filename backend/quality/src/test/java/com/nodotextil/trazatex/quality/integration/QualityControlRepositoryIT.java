package com.nodotextil.trazatex.quality.integration;

import com.nodotextil.trazatex.quality.application.port.out.ClaimedStart;
import com.nodotextil.trazatex.quality.application.port.out.QualityControlRepositoryPort;
import com.nodotextil.trazatex.quality.domain.model.BatchPhase;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.quality.domain.model.ControlStatus;
import com.nodotextil.trazatex.quality.domain.model.QualityControl;
import com.nodotextil.trazatex.quality.domain.model.QualityStatus;
import com.nodotextil.trazatex.quality.domain.model.TestResult;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@code quality_control} y {@code quality_test} contra PostgreSQL real: el ida y vuelta, las restricciones y el reclamo con SKIP LOCKED. */
class QualityControlRepositoryIT extends IntegrationTestBase {

    static final UUID COMPANY = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID INSPECTOR = UUID.fromString("00000000-0000-0000-0000-00000000a0ad");
    static final Duration LEASE = Duration.ofMinutes(2);

    @Autowired
    QualityControlRepositoryPort controls;
    @Autowired
    PlatformTransactionManager transactionManager;

    QualityControl newControl(UUID batchId) {
        BatchProjection batch = BatchProjection.restore(batchId, COMPANY, BatchPhase.AVAILABLE, false, QualityStatus.NOT_REVIEWED, false, 1, START);
        return QualityControl.start(UUID.randomUUID(), batch, INSPECTOR, "  revisar ", START);
    }

    QualityControl starting(Instant due) {
        QualityControl control = newControl(UUID.randomUUID());
        controls.insertStarting(control, due);
        return control;
    }

    <T> T column(UUID id, String column, Class<T> type) {
        return jdbc.sql("SELECT " + column + " FROM quality_control WHERE id = :id").param("id", id).query((rs, n) -> rs.getObject(1, type)).list().get(0);
    }

    @Test
    void aStartingControlSurvivesTheRoundTrip() {
        QualityControl control = newControl(UUID.randomUUID());

        controls.insertStarting(control, START.plusSeconds(30));

        QualityControl loaded = controls.findById(control.id()).orElseThrow();
        assertThat(loaded).usingRecursiveComparison().isEqualTo(control);
        assertThat(loaded.status()).isEqualTo(ControlStatus.STARTING);
        assertThat(loaded.internalObservations()).isEqualTo("revisar");
        assertThat(column(control.id(), "attempts", Integer.class)).isZero();
        assertThat(column(control.id(), "next_attempt_at", OffsetDateTime.class).toInstant()).isEqualTo(START.plusSeconds(30));
    }

    @Test
    void anUnknownControlIsNotFound() {
        assertThat(controls.findById(UUID.randomUUID())).isEmpty();
        assertThat(controls.findByIdForUpdate(UUID.randomUUID())).isEmpty();
    }

    @Test
    void confirmingAndCancellingAreSavedAndStopTheRetryClock() {
        QualityControl confirmed = starting(START);
        QualityControl cancelled = starting(START);

        confirmed.confirmStarted();
        controls.save(confirmed);
        cancelled.cancel("BATCH_IS_FINAL_PRODUCT");
        controls.save(cancelled);

        assertThat(controls.findById(confirmed.id()).orElseThrow().status()).isEqualTo(ControlStatus.IN_PROGRESS);
        assertThat(column(confirmed.id(), "next_attempt_at", OffsetDateTime.class)).isNull();
        QualityControl loaded = controls.findById(cancelled.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(ControlStatus.CANCELLED);
        assertThat(loaded.cancellationReason()).isEqualTo("BATCH_IS_FINAL_PRODUCT");
        assertThat(column(cancelled.id(), "next_attempt_at", OffsetDateTime.class)).isNull();
    }

    @Test
    void aFinishedControlKeepsItsTestsInOrderAndSavingTwiceDoesNotDuplicateThem() {
        QualityControl control = starting(START);
        control.confirmStarted();
        control.addTest(UUID.randomUUID(), "Humedad", "8", "7", "%", TestResult.CONFORMING, null);
        control.addTest(UUID.randomUUID(), "Resistencia", "50", "40", "N", TestResult.FAILED, "bajo");
        control.finish(START.plusSeconds(60));

        controls.save(control);
        controls.save(control);

        QualityControl loaded = controls.findById(control.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(ControlStatus.FINISHED);
        assertThat(loaded.overallResult()).isEqualTo(TestResult.FAILED);
        assertThat(loaded.endDate()).isEqualTo(START.plusSeconds(60));
        assertThat(loaded.tests()).usingRecursiveComparison().isEqualTo(control.tests());
        assertThat(loaded.tests()).extracting(t -> t.criterionName()).containsExactly("Humedad", "Resistencia");
    }

    @Test
    void savingAControlThatDoesNotExistFailsInsteadOfDoingNothing() {
        assertThatThrownBy(() -> controls.save(newControl(UUID.randomUUID()))).isInstanceOf(IllegalStateException.class);
    }

    // ---- un solo control activo por lote ----

    @Test
    void aBatchCannotHaveTwoActiveControlsButCanHaveAnotherOnceTheFirstIsNoLongerActive() {
        UUID batch = UUID.randomUUID();
        QualityControl first = newControl(batch);
        controls.insertStarting(first, START);

        assertThatThrownBy(() -> controls.insertStarting(newControl(batch), START)).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.code()).isEqualTo("CONTROL_ALREADY_ACTIVE"));

        first.cancel("INVALID_PHASE_TRANSITION");
        controls.save(first);
        controls.insertStarting(newControl(batch), START);
        assertThat(jdbc.sql("SELECT count(*) FROM quality_control WHERE batch_id = :b").param("b", batch).query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void anInProgressControlAlsoBlocksAnotherOne() {
        UUID batch = UUID.randomUUID();
        QualityControl first = newControl(batch);
        controls.insertStarting(first, START);
        first.confirmStarted();
        controls.save(first);

        assertThatThrownBy(() -> controls.insertStarting(newControl(batch), START)).isInstanceOf(ConflictException.class);
    }

    // ---- restricciones de la base ----

    @Test
    void theDatabaseRefusesACancelledControlWithoutReasonAndAReasonOnAControlThatIsNotCancelled() {
        QualityControl control = starting(START);

        assertThatThrownBy(() -> jdbc.sql("UPDATE quality_control SET status = 'CANCELLED', next_attempt_at = NULL WHERE id = :id").param("id", control.id()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("UPDATE quality_control SET cancel_reason = 'X' WHERE id = :id").param("id", control.id()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseOnlyLetsAControlHaveARetryClockWhileItIsStarting() {
        QualityControl control = starting(START);

        assertThatThrownBy(() -> jdbc.sql("UPDATE quality_control SET status = 'IN_PROGRESS' WHERE id = :id").param("id", control.id()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("UPDATE quality_control SET next_attempt_at = NULL WHERE id = :id").param("id", control.id()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- reclamo del job ----

    @Test
    void onlyDueStartingControlsAreClaimedOldestFirstUpToTheLimit() {
        QualityControl late = starting(START.plusSeconds(300));
        QualityControl second = starting(START.plusSeconds(20));
        QualityControl first = starting(START.plusSeconds(10));
        QualityControl running = starting(START.plusSeconds(5));
        running.confirmStarted();
        controls.save(running);
        Instant now = START.plusSeconds(60);

        List<ClaimedStart> claimed = controls.claimPendingStarts(now, 1, LEASE);
        List<ClaimedStart> next = controls.claimPendingStarts(now, 10, LEASE);

        assertThat(claimed).extracting(c -> c.control().id()).containsExactly(first.id());
        assertThat(next).extracting(c -> c.control().id()).containsExactly(second.id());
        assertThat(controls.claimPendingStarts(now, 10, LEASE)).as("ni el futuro, ni el que ya está en curso, ni lo ya reclamado").isEmpty();
        assertThat(column(late.id(), "next_attempt_at", OffsetDateTime.class).toInstant()).isEqualTo(START.plusSeconds(300));
    }

    @Test
    void aClaimLeasesTheControlAndWhenTheLeaseExpiresAnotherInstanceTakesItWithItsAttempts() {
        QualityControl control = starting(START);
        controls.recordFailedAttempt(control.id(), 4, START, "HTTP 503");

        ClaimedStart first = controls.claimPendingStarts(START, 10, LEASE).get(0);

        assertThat(first.attempts()).isEqualTo(4);
        assertThat(column(control.id(), "next_attempt_at", OffsetDateTime.class).toInstant()).isEqualTo(START.plus(LEASE));
        assertThat(controls.claimPendingStarts(START.plus(LEASE).minusSeconds(1), 10, LEASE)).as("el arrendamiento sigue vigente").isEmpty();
        assertThat(controls.claimPendingStarts(START.plus(LEASE), 10, LEASE)).as("vencido: otro lo repite").hasSize(1);
    }

    @Test
    void recordingAFailedAttemptSchedulesTheNextOneAndReschedulingDoesNotCountAnAttempt() {
        QualityControl control = starting(START);

        controls.recordFailedAttempt(control.id(), 1, START.plusSeconds(15), "HTTP 503");
        assertThat(column(control.id(), "attempts", Integer.class)).isEqualTo(1);
        assertThat(column(control.id(), "last_error", String.class)).isEqualTo("HTTP 503");

        controls.reschedule(control.id(), START.plusSeconds(99));
        assertThat(column(control.id(), "attempts", Integer.class)).isEqualTo(1);
        assertThat(column(control.id(), "next_attempt_at", OffsetDateTime.class).toInstant()).isEqualTo(START.plusSeconds(99));
    }

    @Test
    void recordingAnAttemptOnAControlThatAlreadyLeftStartingChangesNothing() {
        QualityControl control = starting(START);
        control.confirmStarted();
        controls.save(control);

        controls.recordFailedAttempt(control.id(), 7, START.plusSeconds(15), "tarde");
        controls.reschedule(control.id(), START.plusSeconds(15));

        assertThat(column(control.id(), "attempts", Integer.class)).isZero();
        assertThat(column(control.id(), "next_attempt_at", OffsetDateTime.class)).isNull();
    }

    /**
     * Una instancia reclama dentro de una transacción que aún no termina; otra, a la vez, debe recibir <b>otros</b> controles sin esperar a
     * la primera (SKIP LOCKED), no bloquearse ni repetir los mismos.
     */
    @Test
    void anInstanceHoldingItsClaimDoesNotBlockAnotherOneAndTheyNeverTakeTheSameControl() throws Exception {
        for (int i = 0; i < 6; i++) {
            starting(START);
        }
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch firstHoldsItsClaim = new CountDownLatch(1);
        CountDownLatch secondIsDone = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<ClaimedStart>> first = pool.submit(() -> tx.execute(status -> {
                List<ClaimedStart> mine = controls.claimPendingStarts(START, 3, LEASE);
                firstHoldsItsClaim.countDown();
                try {
                    secondIsDone.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return mine;
            }));
            assertThat(firstHoldsItsClaim.await(20, TimeUnit.SECONDS)).isTrue();

            Future<List<ClaimedStart>> second = pool.submit(() -> tx.execute(status -> controls.claimPendingStarts(START, 3, LEASE)));
            List<ClaimedStart> others = second.get(10, TimeUnit.SECONDS);   // si esperara a la primera, esto vencería
            secondIsDone.countDown();
            List<ClaimedStart> mine = first.get(20, TimeUnit.SECONDS);

            assertThat(mine).hasSize(3);
            assertThat(others).hasSize(3);
            assertThat(mine.stream().map(c -> c.control().id())).doesNotContainAnyElementsOf(others.stream().map(c -> c.control().id()).toList());
        } finally {
            secondIsDone.countDown();
            pool.shutdownNow();
        }
    }
}
