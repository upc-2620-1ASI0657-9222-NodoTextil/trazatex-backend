package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.quality.application.port.out.BatchProjectionRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.ClaimedHold;
import com.nodotextil.trazatex.quality.application.port.out.ImpactedBatchRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.PhaseCommand;
import com.nodotextil.trazatex.quality.application.port.out.PhaseCommandOutcome;
import com.nodotextil.trazatex.quality.application.port.out.PhaseOperation;
import com.nodotextil.trazatex.quality.application.port.out.ProductionPhasePort;
import com.nodotextil.trazatex.quality.application.port.out.QualityEventPublisherPort;
import com.nodotextil.trazatex.quality.domain.event.QualityStatusChanged;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.quality.domain.model.DerivedFailureDecision;
import com.nodotextil.trazatex.quality.domain.model.DerivedFailurePolicy;
import com.nodotextil.trazatex.quality.domain.model.ImpactedBatch;
import com.nodotextil.trazatex.quality.domain.model.LockStatus;
import com.nodotextil.trazatex.quality.domain.model.QualityStatusChange;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resuelve los avisos de falla derivada {@code PENDING}. En cada pasada <b>se vuelve a decidir con la proyección de ahora</b>
 * ({@link DerivedFailurePolicy}): el lote pudo cambiar desde que llegó el aviso.
 * <ul>
 *   <li>{@code HOLD_NOW}: {@code HOLD} a Production fuera de transacción ({@code requestId = failureId}, sin {@code requestedBy}: lo dispara un
 *       evento). Con el 200, en una transacción: el aviso pasa a {@code APPLIED}, la calidad a {@code POTENTIAL_DERIVED_FAILURE} y se escribe
 *       {@code QualityStatusChanged}. <b>Un 409</b> (el lote no está {@code AVAILABLE}: la proyección va atrasada) no cambia nada: el aviso sigue
 *       {@code PENDING}, se registra un WARN y se reintenta con espera creciente; en cada intento se decide de nuevo. Un 404 (el lote no existe en
 *       Production) lo deja {@code NO_ACTION} con ERROR; un 400 es un defecto de Quality: sigue {@code PENDING} con ERROR.</li>
 *   <li>{@code WAIT_FOR_AVAILABLE}: no se llama; se vuelve a mirar en {@code holdWaitPoll} (o antes, si llega un {@code BatchPhaseChanged} del lote).</li>
 *   <li>Lote histórico o ya retenido: {@code NO_ACTION}.</li>
 * </ul>
 * Esta clase no es {@code @Transactional} a propósito: llama a Production.
 */
@Component
class DerivedHoldResolver {

    private static final Logger log = LoggerFactory.getLogger(DerivedHoldResolver.class);

    static final String DERIVED_HOLD_APPLIED = "DERIVED_HOLD_APPLIED";
    static final String DERIVED_HOLD_NO_ACTION = "DERIVED_HOLD_NO_ACTION";

    private final ProductionPhasePort production;
    private final ImpactedBatchRepositoryPort impacted;
    private final BatchProjectionRepositoryPort projections;
    private final QualityEventPublisherPort events;
    private final AuditLogPort audit;
    private final PendingRetryScheduler retries;
    private final PendingCommandSettings settings;
    private final Clock clock;
    private final TransactionTemplate requiresNew;

    DerivedHoldResolver(ProductionPhasePort production, ImpactedBatchRepositoryPort impacted, BatchProjectionRepositoryPort projections,
                        QualityEventPublisherPort events, AuditLogPort audit, PendingRetryScheduler retries, PendingCommandSettings settings, Clock clock,
                        PlatformTransactionManager transactionManager) {
        this.production = production;
        this.impacted = impacted;
        this.projections = projections;
        this.events = events;
        this.audit = audit;
        this.retries = retries;
        this.settings = settings;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    List<ClaimedHold> claim() {
        return requiresNew.execute(tx -> impacted.claimPending(clock.instant(), settings.claimsPerRun(), settings.lease()));
    }

    void resolve(ClaimedHold claimed) {
        ImpactedBatch notice = claimed.impacted();
        UUID failureId = notice.failureId();
        UUID batchId = notice.batchId();
        DerivedFailureDecision decision = DerivedFailurePolicy.decide(projections.findById(batchId).orElse(null));
        switch (decision) {
            case WAIT_FOR_AVAILABLE -> impacted.reschedule(failureId, batchId, clock.instant().plus(settings.holdWaitPoll()));
            case NO_ACTION_HISTORIC, ALREADY_RETAINED -> requiresNew.executeWithoutResult(tx -> noAction(failureId, batchId, decision.name()));
            case HOLD_NOW -> {
                PhaseCommandOutcome outcome = production.send(new PhaseCommand(batchId, PhaseOperation.HOLD, failureId.toString(), null,
                        "POSSIBLE_DERIVED_FAILURE", null));
                requiresNew.executeWithoutResult(tx -> apply(failureId, batchId, outcome, claimed.attempts()));
            }
        }
    }

    private void apply(UUID failureId, UUID batchId, PhaseCommandOutcome outcome, int attemptsSoFar) {
        ImpactedBatch notice = impacted.findForUpdate(failureId, batchId).orElse(null);
        if (notice == null || notice.lockStatus() != LockStatus.PENDING) {
            log.debug("El aviso de la falla {} sobre el lote {} ya estaba resuelto: otro lo resolvió", failureId, batchId);
            return;
        }
        PendingRetryScheduler.Target target = new PendingRetryScheduler.Target() {
            @Override
            public void recordFailedAttempt(int attempts, Instant nextAttemptAt, String error) {
                impacted.recordFailedAttempt(failureId, batchId, attempts, nextAttemptAt, error);
            }

            @Override
            public void reschedule(Instant nextAttemptAt) {
                impacted.reschedule(failureId, batchId, nextAttemptAt);
            }
        };
        PendingRetryScheduler.Subject subject = new PendingRetryScheduler.Subject("aviso de falla derivada", failureId + "/" + batchId, batchId.toString(),
                "PENDING", "Retención por falla derivada sin aplicar");
        switch (outcome) {
            case PhaseCommandOutcome.Confirmed confirmed -> applied(notice);
            case PhaseCommandOutcome.Rejected rejected -> {
                if (rejected.status() == 404) {
                    log.error("Production no conoce el lote {} del aviso de la falla {} ({}): no se puede retener. Aviso en NO_ACTION", batchId, failureId,
                            rejected.code());
                    noAction(failureId, batchId, "BATCH_NOT_FOUND_IN_PRODUCTION");
                } else {
                    log.warn("Production rechazó el HOLD de la falla {} sobre el lote {} ({} {}): la proyección de Quality va atrasada. La calidad no cambia; "
                            + "el aviso sigue PENDING y se vuelve a decidir con la proyección actual", failureId, batchId, rejected.status(), rejected.code());
                    retries.retryLater(target, subject, attemptsSoFar, "HTTP " + rejected.status() + " " + rejected.code());
                }
            }
            case PhaseCommandOutcome.Defective defective -> {
                log.error("Production respondió {} {} al HOLD de la falla {} sobre el lote {}: la petición de Quality está mal (defecto). El aviso sigue PENDING: {}",
                        defective.status(), defective.code(), failureId, batchId, defective.message());
                retries.retryLater(target, subject, attemptsSoFar, "HTTP " + defective.status() + " " + defective.code());
            }
            case PhaseCommandOutcome.Unavailable unavailable -> retries.unavailable(target, subject, unavailable, attemptsSoFar);
        }
    }

    /** El HOLD se confirmó: recién ahora cambia la calidad (POTENTIAL_DERIVED_FAILURE implica qualityHold = true). */
    private void applied(ImpactedBatch notice) {
        Instant now = clock.instant();
        BatchProjection batch = projections.findByIdForUpdate(notice.batchId()).orElse(null);
        QualityStatusChange change;
        try {
            if (batch == null) {
                throw new ConflictException("BATCH_QUALITY_UNKNOWN", "El lote ya no está en la proyección");
            }
            change = batch.applyConfirmedDerivedHold(now);
        } catch (ConflictException keepsItsState) {
            // El lote ya tenía otro estado de calidad que retiene (otra falla, una reevaluación): se conserva y esta retención no cambia nada
            log.warn("Production confirmó el HOLD de la falla {} sobre el lote {}, pero su estado de calidad ({}) no admite el cambio: se conserva",
                    notice.failureId(), notice.batchId(), batch == null ? "desconocido" : batch.qualityStatus());
            noAction(notice.failureId(), notice.batchId(), "ALREADY_RETAINED");
            return;
        }
        projections.update(batch);
        notice.markApplied();
        impacted.save(notice);
        events.publish(QualityStatusChanged.of(batch.batchId(), batch.companyId(), change, null));
        audit.record(null, batch.companyId(), DERIVED_HOLD_APPLIED, "ImpactedBatch", batch.batchId().toString(),
                Map.of("failureId", notice.failureId().toString(), "qualityStatus", change.current().name()));
    }

    private void noAction(UUID failureId, UUID batchId, String reason) {
        ImpactedBatch notice = impacted.findForUpdate(failureId, batchId).orElse(null);
        if (notice == null || notice.lockStatus() != LockStatus.PENDING) {
            return;
        }
        notice.markNoAction();
        impacted.save(notice);
        UUID company = projections.findById(batchId).map(BatchProjection::companyId).orElse(null);
        audit.record(null, company, DERIVED_HOLD_NO_ACTION, "ImpactedBatch", batchId.toString(), Map.of("failureId", failureId.toString(), "reason", reason));
        if ("NO_ACTION_HISTORIC".equals(reason)) {
            log.warn("El lote {} del aviso de la falla {} es histórico: no se hace nada", batchId, failureId);
        } else {
            log.info("El aviso de la falla {} sobre el lote {} queda sin acción ({})", failureId, batchId, reason);
        }
    }
}
