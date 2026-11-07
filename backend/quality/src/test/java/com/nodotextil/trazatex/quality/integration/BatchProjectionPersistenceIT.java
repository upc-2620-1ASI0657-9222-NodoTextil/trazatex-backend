package com.nodotextil.trazatex.quality.integration;

import com.nodotextil.trazatex.quality.application.port.in.SyncBatchProjectionUseCase;
import com.nodotextil.trazatex.quality.application.port.out.BatchProjectionRepositoryPort;
import com.nodotextil.trazatex.quality.domain.model.BatchPhase;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.quality.domain.model.ControlOutcome;
import com.nodotextil.trazatex.quality.domain.model.QualityControl;
import com.nodotextil.trazatex.quality.domain.model.QualityStatus;
import com.nodotextil.trazatex.quality.domain.model.TestResult;
import com.nodotextil.trazatex.sharedmessaging.consumer.ProcessedEventPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@code batch_projection} contra PostgreSQL real: el esquema de Flyway, el ida y vuelta del agregado y la concurrencia por fila. */
class BatchProjectionPersistenceIT extends IntegrationTestBase {

    static final UUID COMPANY = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    @Autowired
    BatchProjectionRepositoryPort batches;
    @Autowired
    SyncBatchProjectionUseCase projection;
    @Autowired
    ProcessedEventPort processedEvents;

    @Test
    void aWholeRowSurvivesTheRoundTrip() {
        BatchProjection batch = BatchProjection.restore(UUID.randomUUID(), COMPANY, BatchPhase.IN_TRANSFER, true,
                QualityStatus.POTENTIAL_DERIVED_FAILURE, true, 12, START);

        batches.insert(batch);

        BatchProjection loaded = batches.findById(batch.batchId()).orElseThrow();
        assertThat(loaded).usingRecursiveComparison().isEqualTo(batch);
    }

    @Test
    void aPartialRowKeepsItsUnknownQualityStatusAsNull() {
        BatchProjection partial = BatchProjection.partial(UUID.randomUUID(), OTHER, BatchPhase.IN_QUALITY_CONTROL, false, 3, START);

        batches.insert(partial);

        assertThat(batches.findById(partial.batchId()).orElseThrow().qualityStatus()).isNull();
        assertThat(jdbc.sql("SELECT quality_status FROM batch_projection WHERE batch_id = :id").param("id", partial.batchId()).query(String.class).optional())
                .isEmpty();
    }

    @Test
    void anUnknownBatchIsNotFound() {
        assertThat(batches.findById(UUID.randomUUID())).isEmpty();
        assertThat(batches.findByIdForUpdate(UUID.randomUUID())).isEmpty();
    }

    @Test
    void updatingARowChangesEveryColumn() {
        BatchProjection batch = BatchProjection.registered(UUID.randomUUID(), COMPANY, START);
        batches.insert(batch);

        batch.applyPhaseChanged(OTHER, BatchPhase.AVAILABLE, true, 9, START.plusSeconds(5));
        batches.update(batch);

        BatchProjection loaded = batches.findById(batch.batchId()).orElseThrow();
        assertThat(loaded.companyId()).isEqualTo(OTHER);
        assertThat(loaded.qualityHold()).isTrue();
        assertThat(loaded.batchVersion()).isEqualTo(9);
        assertThat(loaded.updatedAt()).isEqualTo(START.plusSeconds(5));
    }

    @Test
    void updatingARowThatDoesNotExistFailsInsteadOfDoingNothing() {
        assertThatThrownBy(() -> batches.update(BatchProjection.registered(UUID.randomUUID(), COMPANY, START))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void insertingTheSameBatchTwiceFailsOnThePrimaryKey() {
        BatchProjection batch = BatchProjection.registered(UUID.randomUUID(), COMPANY, START);
        batches.insert(batch);

        assertThatThrownBy(() -> batches.insert(batch)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRefusesAPhaseOrQualityStatusThatIsNotInTheGlossary() {
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO batch_projection (batch_id, company_id, phase, quality_status, quality_hold, updated_at)
                VALUES (:id, :company, 'EN_EVALUACION', NULL, FALSE, now())""").param("id", UUID.randomUUID()).param("company", COMPANY).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO batch_projection (batch_id, company_id, phase, quality_status, quality_hold, updated_at)
                VALUES (:id, :company, 'AVAILABLE', 'Conforme', FALSE, now())""").param("id", UUID.randomUUID()).param("company", COMPANY).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** {@code ever_controlled} llega a la base solo cuando un control terminó; un control iniciado o cancelado no deja huella. */
    @Test
    void everControlledIsStoredAsTrueOnlyAfterAControlFinishes() {
        BatchProjection started = BatchProjection.registered(UUID.randomUUID(), COMPANY, START);
        BatchProjection cancelled = BatchProjection.registered(UUID.randomUUID(), COMPANY, START);
        BatchProjection finished = BatchProjection.registered(UUID.randomUUID(), COMPANY, START);
        List.of(started, cancelled, finished).forEach(batches::insert);

        QualityControl.start(UUID.randomUUID(), started, UUID.randomUUID(), null, START).confirmStarted();
        QualityControl.start(UUID.randomUUID(), cancelled, UUID.randomUUID(), null, START).cancel("COMPANY_INACTIVE");
        QualityControl control = QualityControl.start(UUID.randomUUID(), finished, UUID.randomUUID(), null, START);
        control.confirmStarted();
        control.addTest(UUID.randomUUID(), "Humedad", "8", "7", "%", TestResult.CONFORMING, null);
        ControlOutcome outcome = control.finish(START.plusSeconds(60));
        finished.apply(outcome, START.plusSeconds(61));
        batches.update(finished);

        assertThat(everControlled(started)).isFalse();
        assertThat(everControlled(cancelled)).isFalse();
        assertThat(everControlled(finished)).isTrue();
        assertThat(batches.findById(finished.batchId()).orElseThrow().qualityStatus()).isEqualTo(QualityStatus.CONFORMING);
    }

    private boolean everControlled(BatchProjection batch) {
        return batches.findById(batch.batchId()).orElseThrow().everControlled();
    }

    @Test
    void phaseChangesArrivingAtTheSameTimeLeaveTheHighestVersion() throws Exception {
        UUID batch = UUID.randomUUID();
        projection.registered(batch, COMPANY);
        int versions = 40;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int version = 1; version <= versions; version++) {
                long v = version;
                futures.add(pool.submit(() -> projection.phaseChanged(batch, COMPANY, v % 2 == 0 ? BatchPhase.AVAILABLE : BatchPhase.IN_TRANSFER, v % 2 != 0, v)));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        BatchProjection result = batches.findById(batch).orElseThrow();
        assertThat(result.batchVersion()).isEqualTo(versions);
        assertThat(result.phase()).isEqualTo(BatchPhase.AVAILABLE);
        assertThat(result.qualityHold()).isFalse();
    }

    @Test
    void theSameEventIsRegisteredAsProcessedOnlyOnce() {
        UUID eventId = UUID.randomUUID();

        assertThat(processedEvents.markProcessed(eventId, "BatchRegistered")).isTrue();
        assertThat(processedEvents.markProcessed(eventId, "BatchRegistered")).isFalse();
        assertThat(jdbc.sql("SELECT count(*) FROM processed_events").query(Long.class).single()).isEqualTo(1);
    }
}
