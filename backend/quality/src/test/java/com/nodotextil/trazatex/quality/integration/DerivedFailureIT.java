package com.nodotextil.trazatex.quality.integration;

import com.nodotextil.trazatex.quality.application.dto.AffectedBatch;
import com.nodotextil.trazatex.quality.application.port.in.RecordDerivedFailureUseCase;
import com.nodotextil.trazatex.quality.application.port.in.SyncBatchProjectionUseCase;
import com.nodotextil.trazatex.quality.application.port.out.BatchProjectionRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.ImpactedBatchRepositoryPort;
import com.nodotextil.trazatex.quality.domain.model.BatchPhase;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.quality.domain.model.ImpactType;
import com.nodotextil.trazatex.quality.domain.model.LockStatus;
import com.nodotextil.trazatex.quality.domain.model.QualityStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/**
 * US34 / RF-060 (decisión del usuario, 2026-10-23): POTENTIAL_DERIVED_FAILURE implica qualityHold = true, así que la calidad solo cambia cuando
 * Production confirma el HOLD. El aviso siempre se registra; el job lo retiene cuando se puede, con el mismo requestId (= failureId).
 */
@SpringBootTest(properties = {
        "trazatex.messaging.enabled=false", "trazatex.quality.start.job-enabled=false",
        "trazatex.production.read-timeout=PT0.5S", "trazatex.production.connect-timeout=PT0.5S",
        "trazatex.production.retry.wait-duration=PT0.01S",
        "trazatex.quality.start.claims-per-run=3", "trazatex.quality.start.error-after-attempts=3"})
@ExtendWith(OutputCaptureExtension.class)
class DerivedFailureIT extends FlowTestBase {

    @Autowired
    RecordDerivedFailureUseCase derived;
    @Autowired
    SyncBatchProjectionUseCase sync;
    @Autowired
    ImpactedBatchRepositoryPort notices;
    @MockitoSpyBean
    BatchProjectionRepositoryPort projectionSpy;

    final UUID failure = UUID.randomUUID();
    final UUID source = UUID.randomUUID();

    void detect(UUID failureId, UUID batch) {
        derived.record(failureId, source, List.of(new AffectedBatch(batch, COMPANY_A, source)));
    }

    LockStatus lock(UUID failureId, UUID batch) {
        return LockStatus.valueOf(notice(failureId, batch, "lock_status", String.class));
    }

    // ---- caso A: AVAILABLE y sin retención ----

    @Test
    void anAvailableBatchIsHeldAndOnlyThenItsQualityChanges() throws Exception {
        UUID batch = seed(BatchPhase.AVAILABLE, QualityStatus.CONFORMING, false, true);
        ProductionStub.answersOk();

        detect(failure, batch);

        assertThat(lock(failure, batch)).isEqualTo(LockStatus.PENDING);
        assertThat(projection(batch).qualityStatus()).as("el aviso por sí solo no cambia la calidad").isEqualTo(QualityStatus.CONFORMING);
        assertThat(ProductionStub.allRequests()).as("el consumidor no llama a Production").isEmpty();
        assertThat(at(notice(failure, batch, "next_attempt_at", OffsetDateTime.class))).isEqualTo(START);

        assertThat(recovery.recoverPendingHolds()).isEqualTo(1);

        JsonNode hold = sentBody(sent(batch).get(0));
        assertThat(hold.path("operation").asString()).isEqualTo("HOLD");
        assertThat(hold.path("requestId").asString()).isEqualTo(failure.toString());
        assertThat(hold.has("requestedBy")).as("lo dispara un evento, no una persona").isFalse();
        assertThat(hold.has("qualityHold")).isFalse();
        assertThat(lock(failure, batch)).isEqualTo(LockStatus.APPLIED);
        assertThat(notice(failure, batch, "next_attempt_at", OffsetDateTime.class)).isNull();
        assertThat(notice(failure, batch, "impact_type", String.class)).isEqualTo(ImpactType.DESCENDANT.name());
        assertThat(projection(batch).qualityStatus()).isEqualTo(QualityStatus.POTENTIAL_DERIVED_FAILURE);
        assertThat(projection(batch).qualityHold()).isTrue();
        assertThat(outbox()).hasSize(1);
        assertThat(outbox().get(0).type()).isEqualTo("QualityStatusChanged");
        assertThat(outbox().get(0).topic()).isEqualTo("quality.control");
        assertThat(outbox().get(0).key()).isEqualTo(batch.toString());
        assertThat(outbox().get(0).payload().path("previousStatus").asString()).isEqualTo("CONFORMING");
        assertThat(outbox().get(0).payload().path("newStatus").asString()).isEqualTo("POTENTIAL_DERIVED_FAILURE");
        assertThat(outbox().get(0).payload().path("controlId").isNull()).isTrue();
        assertThat(audit()).extracting(row -> row.get("action")).containsExactlyInAnyOrder("DERIVED_FAILURE_RECORDED", "DERIVED_HOLD_APPLIED");
        assertThat(audit()).allSatisfy(row -> assertThat(row.get("user_id")).as("acción del sistema").isNull());
        assertThat(recovery.recoverPendingHolds()).as("nada más que hacer").isZero();
    }

    // ---- caso B: transferencia en curso (prueba exigida por el usuario) ----

    @Test
    void aBatchInTransferIsHeldInTheReceivingCompanyOnceTheTransferIsAccepted() throws Exception {
        UUID batch = seed(BatchPhase.IN_TRANSFER, QualityStatus.CONFORMING, false, true);
        ProductionStub.answersOk();

        detect(failure, batch);

        assertThat(lock(failure, batch)).as("el aviso se registra pendiente").isEqualTo(LockStatus.PENDING);
        assertThat(projection(batch).qualityStatus()).as("POTENTIAL_DERIVED_FAILURE exige retención: la calidad no cambia").isEqualTo(QualityStatus.CONFORMING);
        assertThat(projection(batch).qualityHold()).isFalse();
        assertThat(at(notice(failure, batch, "next_attempt_at", OffsetDateTime.class))).as("espera lenta: no hay nada que mandar todavía").isEqualTo(START.plus(Duration.ofMinutes(5)));
        assertThat(recovery.recoverPendingHolds()).isZero();
        assertThat(ProductionStub.allRequests()).isEmpty();
        assertThat(outbox()).isEmpty();

        // Se acepta la transferencia: Production publica BatchPhaseChanged → AVAILABLE con la empresa receptora
        sync.phaseChanged(batch, COMPANY_B, BatchPhase.AVAILABLE, false, 4);
        assertThat(at(notice(failure, batch, "next_attempt_at", OffsetDateTime.class))).as("el cambio del lote despierta el aviso").isEqualTo(START);
        assertThat(recovery.recoverPendingHolds()).isEqualTo(1);

        assertThat(sent(batch)).hasSize(1);
        assertThat(sentBody(sent(batch).get(0)).path("requestId").asString()).isEqualTo(failure.toString());
        assertThat(lock(failure, batch)).isEqualTo(LockStatus.APPLIED);
        assertThat(projection(batch).companyId()).as("retenido en la empresa receptora").isEqualTo(COMPANY_B);
        assertThat(projection(batch).qualityStatus()).isEqualTo(QualityStatus.POTENTIAL_DERIVED_FAILURE);
        assertThat(projection(batch).qualityHold()).isTrue();
        assertThat(outbox().get(0).payload().path("companyId").asString()).isEqualTo(COMPANY_B.toString());
    }

    @Test
    void aBatchInAnyOtherNonHistoricPhaseAlsoWaitsAndInQualityControlItsReevaluationIsNotInterrupted(CapturedOutput output) throws Exception {
        for (BatchPhase phase : new BatchPhase[]{BatchPhase.IN_TRANSFORMATION, BatchPhase.IN_QUALITY_CONTROL}) {
            UUID batch = seed(phase, QualityStatus.PENDING_REEVALUATION, true, true);
            detect(UUID.randomUUID(), batch);
            assertThat(recovery.recoverPendingHolds()).as(phase.name()).isZero();
            assertThat(projection(batch).qualityStatus()).isEqualTo(QualityStatus.PENDING_REEVALUATION);
        }
        assertThat(ProductionStub.allRequests()).isEmpty();
        assertThat(output.getAll()).contains("WAIT_FOR_AVAILABLE");
    }

    // ---- caso C: histórico ----

    @Test
    void aHistoricBatchIsRecordedWithoutActionAndWithAWarning(CapturedOutput output) throws Exception {
        for (BatchPhase phase : new BatchPhase[]{BatchPhase.PROCESSED, BatchPhase.SPLIT, BatchPhase.DISCARDED}) {
            UUID batch = seed(phase, QualityStatus.NOT_REVIEWED, false, false);
            UUID aFailure = UUID.randomUUID();

            detect(aFailure, batch);

            assertThat(lock(aFailure, batch)).as(phase.name()).isEqualTo(LockStatus.NO_ACTION);
            assertThat(notice(aFailure, batch, "next_attempt_at", OffsetDateTime.class)).isNull();
            assertThat(projection(batch).qualityStatus()).isEqualTo(QualityStatus.NOT_REVIEWED);
        }
        assertThat(recovery.recoverAll()).isZero();
        assertThat(ProductionStub.allRequests()).isEmpty();
        assertThat(output.getAll()).contains("NO_ACTION_HISTORIC");
    }

    // ---- ya retenido ----

    @Test
    void aBatchAlreadyRetainedKeepsItsStateAndTheSecondFailureOverItIsNoAction() throws Exception {
        UUID batch = seedAvailable();
        UUID other = UUID.randomUUID();
        ProductionStub.answersOk();
        detect(failure, batch);
        recovery.recoverPendingHolds();
        assertThat(lock(failure, batch)).isEqualTo(LockStatus.APPLIED);

        detect(other, batch);

        assertThat(lock(other, batch)).as("ya está retenido: sin acción").isEqualTo(LockStatus.NO_ACTION);
        assertThat(notices.findByFailure(other)).extracting(n -> n.impactType()).containsExactly(ImpactType.DESCENDANT);
        assertThat(recovery.recoverPendingHolds()).isZero();
        assertThat(sent(batch)).as("un solo HOLD").hasSize(1);
        assertThat(outbox()).as("un solo cambio de calidad").hasSize(1);
        assertThat(projection(batch).qualityStatus()).isEqualTo(QualityStatus.POTENTIAL_DERIVED_FAILURE);
    }

    @Test
    void failedPendingReevaluationAndPotentialStatesAreKeptAndGetNoHold() throws Exception {
        for (QualityStatus status : new QualityStatus[]{QualityStatus.FAILED, QualityStatus.PENDING_REEVALUATION, QualityStatus.POTENTIAL_DERIVED_FAILURE}) {
            UUID batch = seed(BatchPhase.AVAILABLE, status, true, true);
            UUID aFailure = UUID.randomUUID();
            detect(aFailure, batch);
            assertThat(lock(aFailure, batch)).as(status.name()).isEqualTo(LockStatus.NO_ACTION);
            assertThat(projection(batch).qualityStatus()).isEqualTo(status);
        }
        assertThat(ProductionStub.allRequests()).isEmpty();
    }

    // ---- HOLD con 409: la proyección va atrasada ----

    @Test
    void aHoldRejectedWith409KeepsTheNoticePendingWithoutChangingTheQualityAndDecidesAgainWithTheCorrectedProjection(CapturedOutput output) throws Exception {
        UUID batch = seedAvailable();   // la proyección cree que está disponible...
        ProductionStub.answers(409, ProductionStub.apiError("INVALID_PHASE_TRANSITION"));   // ...pero Production dice que no
        detect(failure, batch);

        assertThat(recovery.recoverPendingHolds()).isEqualTo(1);

        assertThat(lock(failure, batch)).isEqualTo(LockStatus.PENDING);
        assertThat(notice(failure, batch, "attempts", Integer.class)).isEqualTo(1);
        assertThat(notice(failure, batch, "last_error", String.class)).contains("409").contains("INVALID_PHASE_TRANSITION");
        assertThat(at(notice(failure, batch, "next_attempt_at", OffsetDateTime.class))).isEqualTo(START.plusSeconds(15));
        assertThat(projection(batch).qualityStatus()).as("la calidad no cambia").isEqualTo(QualityStatus.NOT_REVIEWED);
        assertThat(outbox()).isEmpty();
        assertThat(output.getAll()).contains("la proyección de Quality va atrasada");

        // Llega lo que la proyección no sabía: el lote está en transferencia → se espera sin llamar
        sync.phaseChanged(batch, COMPANY_A, BatchPhase.IN_TRANSFER, false, 2);
        assertThat(recovery.recoverPendingHolds()).isEqualTo(1);
        assertThat(sent(batch)).as("no se vuelve a llamar con el lote en transferencia").hasSize(1);
        assertThat(lock(failure, batch)).isEqualTo(LockStatus.PENDING);

        // Vuelve a AVAILABLE: ahora sí se retiene
        ProductionStub.reset();
        ProductionStub.answersOk();
        sync.phaseChanged(batch, COMPANY_A, BatchPhase.AVAILABLE, false, 3);
        assertThat(recovery.recoverPendingHolds()).isEqualTo(1);
        assertThat(lock(failure, batch)).isEqualTo(LockStatus.APPLIED);
        assertThat(projection(batch).qualityStatus()).isEqualTo(QualityStatus.POTENTIAL_DERIVED_FAILURE);
    }

    @Test
    void aHold409ForABatchThatTurnsOutToBeHistoricEndsWithoutAction() throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answers(409, ProductionStub.apiError("INVALID_PHASE_TRANSITION"));
        detect(failure, batch);
        recovery.recoverPendingHolds();
        assertThat(lock(failure, batch)).isEqualTo(LockStatus.PENDING);

        sync.phaseChanged(batch, COMPANY_A, BatchPhase.PROCESSED, false, 2);
        recovery.recoverPendingHolds();

        assertThat(lock(failure, batch)).isEqualTo(LockStatus.NO_ACTION);
        assertThat(sent(batch)).hasSize(1);
    }

    @Test
    void aHold404IsNoActionWithAnErrorAndABadRequestKeepsTheNoticePendingWithAnError(CapturedOutput output) throws Exception {
        UUID missing = seedAvailable();
        ProductionStub.answers(404, ProductionStub.apiError("BATCH_NOT_FOUND"));
        detect(failure, missing);
        recovery.recoverPendingHolds();
        assertThat(lock(failure, missing)).isEqualTo(LockStatus.NO_ACTION);
        assertThat(output.getAll()).contains("Production no conoce el lote");

        ProductionStub.reset();
        UUID defective = seedAvailable();
        UUID otherFailure = UUID.randomUUID();
        ProductionStub.answers(400, ProductionStub.apiError("MISSING_FIELDS"));
        detect(otherFailure, defective);
        recovery.recoverPendingHolds();
        assertThat(lock(otherFailure, defective)).isEqualTo(LockStatus.PENDING);
        assertThat(projection(defective).qualityStatus()).isEqualTo(QualityStatus.NOT_REVIEWED);
        assertThat(output.getAll()).contains("la petición de Quality está mal");
    }

    @Test
    void withoutAnAnswerFromProductionTheHoldIsRetriedWithTheSameRequestIdAndAGrowingWait() throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answers(503, ProductionStub.apiError("TEMPORARILY_UNAVAILABLE"));
        detect(failure, batch);

        recovery.recoverPendingHolds();
        assertThat(at(notice(failure, batch, "next_attempt_at", OffsetDateTime.class))).isEqualTo(START.plusSeconds(15));
        clock.advance(Duration.ofSeconds(15));
        productionBreaker.reset();
        recovery.recoverPendingHolds();
        assertThat(at(notice(failure, batch, "next_attempt_at", OffsetDateTime.class))).isEqualTo(START.plusSeconds(15 + 30));
        assertThat(lock(failure, batch)).isEqualTo(LockStatus.PENDING);
        assertThat(projection(batch).qualityStatus()).isEqualTo(QualityStatus.NOT_REVIEWED);

        ProductionStub.reset();
        ProductionStub.answersOk();
        clock.advance(Duration.ofSeconds(30));
        recovery.recoverPendingHolds();

        assertThat(lock(failure, batch)).isEqualTo(LockStatus.APPLIED);
        assertThat(sent(batch).stream().map(r -> sentBody(r).path("requestId").asString()).distinct()).containsExactly(failure.toString());
        assertThat(outbox()).hasSize(1);
    }

    // ---- registro del aviso ----

    @Test
    void theSameNoticeTwiceIsRecordedOnce() throws Exception {
        UUID batch = seedAvailable();

        detect(failure, batch);
        detect(failure, batch);

        assertThat(notices.findByFailure(failure)).hasSize(1);
        assertThat(auditActions().stream().filter("DERIVED_FAILURE_RECORDED"::equals).count()).isEqualTo(1);
    }

    @Test
    void aBatchQualityDoesNotKnowYetWaitsAndIsHeldWhenItsRegistrationArrives() throws Exception {
        UUID batch = UUID.randomUUID();
        ProductionStub.answersOk();
        detect(failure, batch);
        assertThat(lock(failure, batch)).isEqualTo(LockStatus.PENDING);
        assertThat(recovery.recoverPendingHolds()).isZero();

        sync.registered(batch, COMPANY_A);
        assertThat(recovery.recoverPendingHolds()).isEqualTo(1);

        assertThat(lock(failure, batch)).isEqualTo(LockStatus.APPLIED);
        assertThat(projection(batch).qualityStatus()).isEqualTo(QualityStatus.POTENTIAL_DERIVED_FAILURE);
    }

    @Test
    void ifTheBatchHasAnotherRetainingStateByTheTimeTheHoldIsConfirmedTheStateIsKeptAndTheNoticeIsNoAction(CapturedOutput output) throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answersOk();
        detect(failure, batch);
        // La decisión se toma con una copia vieja (disponible y sin calidad retenida) mientras la fila real ya cambió a FAILED
        BatchProjection stale = projection(batch);
        projections.update(BatchProjection.restore(batch, COMPANY_A, BatchPhase.AVAILABLE, true, QualityStatus.FAILED, true, 2, START));
        Mockito.doReturn(Optional.of(stale)).doCallRealMethod().when(projectionSpy).findById(any());

        recovery.recoverPendingHolds();

        assertThat(lock(failure, batch)).isEqualTo(LockStatus.NO_ACTION);
        assertThat(projection(batch).qualityStatus()).isEqualTo(QualityStatus.FAILED);
        assertThat(outbox()).isEmpty();
        assertThat(output.getAll()).contains("no admite el cambio");
    }
}
