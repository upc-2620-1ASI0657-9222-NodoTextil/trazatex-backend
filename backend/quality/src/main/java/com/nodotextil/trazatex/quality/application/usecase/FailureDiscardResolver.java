package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.quality.application.port.out.PhaseCommand;
import com.nodotextil.trazatex.quality.application.port.out.PhaseCommandOutcome;
import com.nodotextil.trazatex.quality.application.port.out.PhaseOperation;
import com.nodotextil.trazatex.quality.application.port.out.ProductionPhasePort;
import com.nodotextil.trazatex.quality.application.port.out.QualityEventPublisherPort;
import com.nodotextil.trazatex.quality.application.port.out.QualityFailureRepositoryPort;
import com.nodotextil.trazatex.quality.domain.event.FailureConfirmed;
import com.nodotextil.trazatex.quality.domain.model.QualityFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Resuelve el descarte pendiente de una falla: manda {@code DISCARD} a Production <b>fuera de toda transacción</b> con
 * {@code requestId = failureId} y aplica la respuesta en una transacción propia, igual que {@link ControlFinishResolver}. La falla solo queda
 * {@code CONFIRMED} —y {@code FailureConfirmed} solo se escribe en el outbox— con la confirmación de Production, en la misma transacción. Un 409, 404
 * o 400 descarta el descarte pendiente (la falla sigue {@code OPEN}) y registra un ERROR.
 */
@Component
class FailureDiscardResolver {

    private static final Logger log = LoggerFactory.getLogger(FailureDiscardResolver.class);

    static final String FAILURE_CONFIRMED = "FAILURE_CONFIRMED";
    static final String FAILURE_DISCARD_REJECTED = "FAILURE_DISCARD_REJECTED";

    private final ProductionPhasePort production;
    private final QualityFailureRepositoryPort failures;
    private final QualityEventPublisherPort events;
    private final AuditLogPort audit;
    private final PendingRetryScheduler retries;
    private final Clock clock;
    private final TransactionTemplate requiresNew;

    FailureDiscardResolver(ProductionPhasePort production, QualityFailureRepositoryPort failures, QualityEventPublisherPort events, AuditLogPort audit,
                           PendingRetryScheduler retries, Clock clock, PlatformTransactionManager transactionManager) {
        this.production = production;
        this.failures = failures;
        this.events = events;
        this.audit = audit;
        this.retries = retries;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Cómo terminó: lo que Production respondió y la falla tal como quedó. */
    record Resolution(ControlResolution.Kind kind, QualityFailure failure, int httpStatus, String code, String message) {
    }

    Resolution resolve(QualityFailure failure, int attemptsSoFar) {
        PhaseCommandOutcome outcome = production.send(new PhaseCommand(failure.batchId(), PhaseOperation.DISCARD, failure.id().toString(), null, null,
                failure.discardRequestedBy()));
        return requiresNew.execute(tx -> apply(failure.id(), outcome, attemptsSoFar));
    }

    private Resolution apply(UUID failureId, PhaseCommandOutcome outcome, int attemptsSoFar) {
        QualityFailure failure = failures.findByIdForUpdate(failureId)
                .orElseThrow(() -> new IllegalStateException("La falla " + failureId + " desapareció mientras se resolvía"));
        if (!failure.discardPending()) {
            log.debug("El descarte de la falla {} ya estaba resuelto ({}): otro lo resolvió", failureId, failure.status());
            return new Resolution(ControlResolution.Kind.ALREADY_RESOLVED, failure, 0, null, null);
        }
        UUID requester = failure.discardRequestedBy();
        return switch (outcome) {
            case PhaseCommandOutcome.Confirmed confirmed -> {
                failure.completeDiscard();
                failures.save(failure);
                events.publish(FailureConfirmed.of(failure, failure.companyId(), clock.instant()));
                events.publish(com.nodotextil.trazatex.quality.domain.event.QualityFailureUpdated.of(failure));
                audit.record(requester, failure.companyId(), FAILURE_CONFIRMED, "QualityFailure", failureId.toString(),
                        Map.of("batchId", failure.batchId().toString(), "affectedQtyKg", failure.affectedQtyKg().toPlainString()));
                yield new Resolution(ControlResolution.Kind.CONFIRMED, failure, 200, null, null);
            }
            case PhaseCommandOutcome.Rejected rejected -> {
                reject(failure, rejected.code());
                log.error("Production rechazó DISCARD de la falla {} del lote {} ({} {}): la falla sigue OPEN y el lote no está donde Quality cree. "
                        + "Ver el procedimiento «RELEASE o DISCARD rechazado» en docs/progress.md", failureId, failure.batchId(), rejected.status(),
                        rejected.code());
                yield new Resolution(ControlResolution.Kind.REJECTED, failure, rejected.status(), rejected.code(), rejected.message());
            }
            case PhaseCommandOutcome.Defective defective -> {
                reject(failure, defective.code());
                log.error("Production respondió {} {} a DISCARD de la falla {} del lote {}: la petición de Quality está mal (defecto, no se reintenta): {}",
                        defective.status(), defective.code(), failureId, failure.batchId(), defective.message());
                yield new Resolution(ControlResolution.Kind.DEFECT, failure, defective.status(), defective.code(), defective.message());
            }
            case PhaseCommandOutcome.Unavailable unavailable -> {
                retries.unavailable(new PendingRetryScheduler.Target() {
                    @Override
                    public void recordFailedAttempt(int attempts, Instant nextAttemptAt, String error) {
                        failures.recordFailedAttempt(failureId, attempts, nextAttemptAt, error);
                    }

                    @Override
                    public void reschedule(Instant nextAttemptAt) {
                        failures.reschedule(failureId, nextAttemptAt);
                    }
                }, new PendingRetryScheduler.Subject("descarte de la falla", failureId.toString(), failure.batchId().toString(), "OPEN con el descarte pendiente",
                        "Finalización o descarte sin respuesta"), unavailable, attemptsSoFar);
                yield new Resolution(ControlResolution.Kind.PENDING, failure, 0, null, null);
            }
        };
    }

    private void reject(QualityFailure failure, String code) {
        UUID requester = failure.discardRequestedBy();
        failure.abortDiscard();
        failures.save(failure);
        audit.record(requester, failure.companyId(), FAILURE_DISCARD_REJECTED, "QualityFailure", failure.id().toString(),
                Map.of("batchId", failure.batchId().toString(), "reason", code));
    }
}
