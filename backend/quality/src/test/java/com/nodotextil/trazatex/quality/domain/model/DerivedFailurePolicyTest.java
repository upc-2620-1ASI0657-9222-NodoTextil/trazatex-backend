package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static com.nodotextil.trazatex.quality.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.quality.domain.Fixtures.NOW;
import static com.nodotextil.trazatex.quality.domain.Fixtures.OTHER_COMPANY;
import static com.nodotextil.trazatex.quality.domain.Fixtures.batch;
import static com.nodotextil.trazatex.quality.domain.Fixtures.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US34 / RF-060 (decisión del usuario, 2026-10-23): POTENTIAL_DERIVED_FAILURE implica qualityHold = true. */
class DerivedFailurePolicyTest {

    // ---- los casos A, B y C ----

    @Test
    void caseA_anAvailableBatchWithoutHoldIsHeldNowAndTheAlertStartsPending() {
        for (QualityStatus status : new QualityStatus[]{QualityStatus.NOT_REVIEWED, QualityStatus.CONFORMING}) {
            DerivedFailureDecision decision = DerivedFailurePolicy.decide(batch(BatchPhase.AVAILABLE, status, false, false));

            assertThat(decision).isEqualTo(DerivedFailureDecision.HOLD_NOW);
            assertThat(decision.sendsHold()).isTrue();
            assertThat(decision.initialLockStatus()).isEqualTo(LockStatus.PENDING);
        }
    }

    @ParameterizedTest
    @EnumSource(value = BatchPhase.class, names = {"IN_TRANSFORMATION", "IN_TRANSFER", "IN_QUALITY_CONTROL"})
    void caseB_aBatchThatIsNotAvailableYetWaitsWithoutHoldAndWithAWarning(BatchPhase phase) {
        for (QualityStatus status : QualityStatus.values()) {
            DerivedFailureDecision decision = DerivedFailurePolicy.decide(batch(phase, status, status.retainsBatch(), true));

            assertThat(decision).as("%s / %s", phase, status).isEqualTo(DerivedFailureDecision.WAIT_FOR_AVAILABLE);
            assertThat(decision.sendsHold()).isFalse();
            assertThat(decision.warns()).isTrue();
            assertThat(decision.initialLockStatus()).isEqualTo(LockStatus.PENDING);
        }
    }

    @ParameterizedTest
    @EnumSource(value = BatchPhase.class, names = {"PROCESSED", "SPLIT", "DISCARDED"})
    void caseC_aHistoricBatchGetsNoActionWithAWarning(BatchPhase phase) {
        for (QualityStatus status : QualityStatus.values()) {
            DerivedFailureDecision decision = DerivedFailurePolicy.decide(batch(phase, status, status.retainsBatch(), false));

            assertThat(decision).isEqualTo(DerivedFailureDecision.NO_ACTION_HISTORIC);
            assertThat(decision.sendsHold()).isFalse();
            assertThat(decision.warns()).isTrue();
            assertThat(decision.initialLockStatus()).isEqualTo(LockStatus.NO_ACTION);
        }
    }

    @ParameterizedTest
    @EnumSource(value = QualityStatus.class, names = {"FAILED", "PENDING_REEVALUATION", "POTENTIAL_DERIVED_FAILURE"})
    void anAvailableBatchAlreadyRetainedKeepsItsStateAndGetsNoHold(QualityStatus status) {
        DerivedFailureDecision decision = DerivedFailurePolicy.decide(batch(BatchPhase.AVAILABLE, status, true, true));

        assertThat(decision).isEqualTo(DerivedFailureDecision.ALREADY_RETAINED);
        assertThat(decision.sendsHold()).isFalse();
        assertThat(decision.initialLockStatus()).isEqualTo(LockStatus.NO_ACTION);
    }

    @Test
    void aBatchOnHoldIsNeverHeldAgainWhateverItsQualityStatus() {
        for (QualityStatus status : QualityStatus.values()) {
            assertThat(DerivedFailurePolicy.decide(batch(BatchPhase.AVAILABLE, status, true, false)).sendsHold()).isFalse();
        }
    }

    @Test
    void aBatchQualityDoesNotKnowYetCannotBeHeldBlindly() {
        assertThat(DerivedFailurePolicy.decide(null)).isEqualTo(DerivedFailureDecision.WAIT_FOR_AVAILABLE);
        assertThat(DerivedFailurePolicy.decide(BatchProjection.partial(id(), COMPANY, BatchPhase.AVAILABLE, false, 2, NOW)))
                .isEqualTo(DerivedFailureDecision.WAIT_FOR_AVAILABLE);
    }

    @Test
    void onlyOneDecisionSendsTheHold() {
        long sending = java.util.Arrays.stream(DerivedFailureDecision.values()).filter(DerivedFailureDecision::sendsHold).count();
        assertThat(sending).isEqualTo(1);
    }

    // ---- ImpactedBatch ----

    @Test
    void theAlertIsAlwaysRecordedWithItsImpactTypeAndTheDecisionsLockStatus() {
        UUID failure = id();
        UUID source = id();
        UUID affected = id();

        ImpactedBatch descendant = ImpactedBatch.detect(failure, source, affected, source, DerivedFailureDecision.HOLD_NOW);
        ImpactedBatch historic = ImpactedBatch.detect(failure, source, affected, id(), DerivedFailureDecision.NO_ACTION_HISTORIC);

        assertThat(descendant.failureId()).isEqualTo(failure);
        assertThat(descendant.batchId()).isEqualTo(affected);
        assertThat(descendant.impactType()).isEqualTo(ImpactType.DESCENDANT);
        assertThat(descendant.lockStatus()).isEqualTo(LockStatus.PENDING);
        assertThat(historic.impactType()).isEqualTo(ImpactType.RELATED_BRANCH);
        assertThat(historic.lockStatus()).isEqualTo(LockStatus.NO_ACTION);
    }

    @Test
    void aPendingAlertBecomesAppliedOrNoActionAndThenNeverChangesAgain() {
        ImpactedBatch applied = ImpactedBatch.detect(id(), id(), id(), id(), DerivedFailureDecision.HOLD_NOW);
        ImpactedBatch noAction = ImpactedBatch.detect(id(), id(), id(), id(), DerivedFailureDecision.WAIT_FOR_AVAILABLE);

        applied.markApplied();
        noAction.markNoAction();

        assertThat(applied.lockStatus()).isEqualTo(LockStatus.APPLIED);
        assertThat(noAction.lockStatus()).isEqualTo(LockStatus.NO_ACTION);
        for (ImpactedBatch done : new ImpactedBatch[]{applied, noAction}) {
            assertThatThrownBy(done::markApplied).isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.INVALID_LOCK_TRANSITION));
            assertThatThrownBy(done::markNoAction).isInstanceOf(TrazatexException.class);
        }
    }

    // ---- Una transferencia en curso (decisión del usuario, 2026-10-23) ----

    /**
     * El lote está en una transferencia en curso cuando llega el aviso: no se retiene ni cambia su calidad (POTENTIAL_DERIVED_FAILURE
     * exige retención). Al aceptarse, Production publica BatchPhaseChanged → AVAILABLE con la empresa receptora; entonces se vuelve
     * a decidir, se manda el HOLD y recién con su confirmación cambia la calidad: el lote queda retenido en la empresa receptora.
     * (El flujo completo con Production y Kafka reales se prueba en 06c.)
     */
    @Test
    void aBatchInTransferIsHeldInTheReceivingCompanyOnceTheTransferIsAccepted() {
        UUID failure = id();
        UUID source = id();
        BatchProjection batch = BatchProjection.restore(id(), COMPANY, BatchPhase.IN_TRANSFER, false, QualityStatus.CONFORMING, true, 3, NOW);

        // 1. llega el aviso con el lote en transferencia
        DerivedFailureDecision first = DerivedFailurePolicy.decide(batch);
        ImpactedBatch alert = ImpactedBatch.detect(failure, source, batch.batchId(), source, first);
        assertThat(first).isEqualTo(DerivedFailureDecision.WAIT_FOR_AVAILABLE);
        assertThat(alert.lockStatus()).isEqualTo(LockStatus.PENDING);
        assertThat(batch.qualityStatus()).as("la calidad no cambia sin retención").isEqualTo(QualityStatus.CONFORMING);
        assertThat(batch.qualityHold()).isFalse();

        // 2. se acepta la transferencia: el lote vuelve a AVAILABLE, ahora en la empresa receptora
        assertThat(batch.applyPhaseChanged(OTHER_COMPANY, BatchPhase.AVAILABLE, false, 4, NOW.plusSeconds(30))).isTrue();
        DerivedFailureDecision second = DerivedFailurePolicy.decide(batch);
        assertThat(second).isEqualTo(DerivedFailureDecision.HOLD_NOW);

        // 3. el HOLD (requestId = failureId) se confirma: recién ahí cambia la calidad y el aviso queda aplicado
        QualityStatusChange change = batch.applyConfirmedDerivedHold(NOW.plusSeconds(31));
        alert.markApplied();

        assertThat(change).isEqualTo(new QualityStatusChange(QualityStatus.CONFORMING, QualityStatus.POTENTIAL_DERIVED_FAILURE));
        assertThat(batch.companyId()).as("retenido en la empresa receptora").isEqualTo(OTHER_COMPANY);
        assertThat(batch.qualityHold()).isTrue();
        assertThat(alert.lockStatus()).isEqualTo(LockStatus.APPLIED);
    }

    @Test
    void aBatchInTransferThatTheTransferLeavesAlreadyRetainedNeedsNoHold() {
        BatchProjection batch = BatchProjection.restore(id(), COMPANY, BatchPhase.IN_TRANSFER, false, QualityStatus.NOT_REVIEWED, false, 3, NOW);
        batch.applyPhaseChanged(OTHER_COMPANY, BatchPhase.AVAILABLE, true, 4, NOW);   // otra falla lo retuvo mientras tanto

        assertThat(DerivedFailurePolicy.decide(batch)).isEqualTo(DerivedFailureDecision.ALREADY_RETAINED);
    }
}
