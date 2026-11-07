package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.quality.application.port.out.BatchProjectionRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.PhaseCommand;
import com.nodotextil.trazatex.quality.application.port.out.PhaseCommandOutcome;
import com.nodotextil.trazatex.quality.application.port.out.PhaseOperation;
import com.nodotextil.trazatex.quality.application.port.out.ProductionPhasePort;
import com.nodotextil.trazatex.quality.application.port.out.QualityControlRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.QualityEventPublisherPort;
import com.nodotextil.trazatex.quality.application.port.out.QualityFailureRepositoryPort;
import com.nodotextil.trazatex.quality.domain.event.QualityControlCompleted;
import com.nodotextil.trazatex.quality.domain.event.QualityStatusChanged;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.quality.domain.model.ControlOutcome;
import com.nodotextil.trazatex.quality.domain.model.FailureChange;
import com.nodotextil.trazatex.quality.domain.model.FailureLifecycle;
import com.nodotextil.trazatex.quality.domain.model.QualityControl;
import com.nodotextil.trazatex.quality.domain.model.QualityStatusChange;
import com.nodotextil.trazatex.quality.domain.model.TestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Resuelve la finalización pendiente de un control: manda {@code RELEASE} a Production <b>fuera de toda transacción</b> con
 * {@code requestId = controlId} y la retención del resultado congelado, y aplica la respuesta en una transacción propia. Lo usan la petición del
 * usuario y el job, con el mismo {@code requestId}: Production es idempotente y devuelve lo que ya guardó, así que repetirlo es seguro.
 *
 * <p><b>El control solo cambia de estado con la confirmación.</b> Con un 200, en <b>una</b> transacción: el control pasa a {@code FINISHED}, la
 * proyección recibe el resultado (calidad, retención, {@code everControlled}), la falla se genera, se reutiliza o se resuelve, y se escriben en el
 * outbox {@code QualityControlCompleted} y {@code QualityStatusChanged}. Sin respuesta, nada de eso ocurre y se reintenta. Con un 409, 404 o 400 la
 * finalización pendiente se descarta (el control sigue {@code IN_PROGRESS} y sus pruebas vuelven a admitir cambios) y se registra un ERROR: el lote
 * no está donde Quality cree y hay que reconciliarlo (procedimiento en docs/progress.md).
 */
@Component
class ControlFinishResolver {

    private static final Logger log = LoggerFactory.getLogger(ControlFinishResolver.class);

    static final String CONTROL_FINISHED = "CONTROL_FINISHED";
    static final String CONTROL_FINISH_REJECTED = "CONTROL_FINISH_REJECTED";
    static final String FAILURE_GENERATED = "FAILURE_GENERATED";
    static final String FAILURE_REOPENED = "FAILURE_REOPENED";
    static final String FAILURE_RESOLVED = "FAILURE_RESOLVED";

    private final ProductionPhasePort production;
    private final QualityControlRepositoryPort controls;
    private final BatchProjectionRepositoryPort projections;
    private final QualityFailureRepositoryPort failures;
    private final QualityEventPublisherPort events;
    private final AuditLogPort audit;
    private final PendingRetryScheduler retries;
    private final Clock clock;
    private final TransactionTemplate requiresNew;

    ControlFinishResolver(ProductionPhasePort production, QualityControlRepositoryPort controls, BatchProjectionRepositoryPort projections,
                          QualityFailureRepositoryPort failures, QualityEventPublisherPort events, AuditLogPort audit, PendingRetryScheduler retries,
                          Clock clock, PlatformTransactionManager transactionManager) {
        this.production = production;
        this.controls = controls;
        this.projections = projections;
        this.failures = failures;
        this.events = events;
        this.audit = audit;
        this.retries = retries;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** @param attemptsSoFar intentos sin respuesta que ya lleva la finalización (0 en la petición que la pidió) */
    ControlResolution resolve(QualityControl control, int attemptsSoFar) {
        PhaseCommandOutcome outcome = production.send(new PhaseCommand(control.batchId(), PhaseOperation.RELEASE, control.id().toString(),
                control.finishResult() == TestResult.FAILED, null, control.finishRequestedBy()));
        return requiresNew.execute(tx -> apply(control.id(), outcome, attemptsSoFar));
    }

    private ControlResolution apply(UUID controlId, PhaseCommandOutcome outcome, int attemptsSoFar) {
        QualityControl control = controls.findByIdForUpdate(controlId)
                .orElseThrow(() -> new IllegalStateException("El control " + controlId + " desapareció mientras se resolvía"));
        if (!control.finishPending()) {
            log.debug("La finalización del control {} ya estaba resuelta ({}): otro la resolvió", controlId, control.status());
            return ControlResolution.alreadyResolved(control);
        }
        return switch (outcome) {
            case PhaseCommandOutcome.Confirmed confirmed -> complete(control);
            case PhaseCommandOutcome.Rejected rejected -> {
                reject(control, rejected.code());
                log.error("Production rechazó RELEASE del control {} del lote {} ({} {}): el control sigue IN_PROGRESS y el lote no está donde Quality cree. "
                        + "Ver el procedimiento «RELEASE o DISCARD rechazado» en docs/progress.md", controlId, control.batchId(), rejected.status(),
                        rejected.code());
                yield ControlResolution.rejected(control, rejected.status(), rejected.code(), rejected.message());
            }
            case PhaseCommandOutcome.Defective defective -> {
                reject(control, defective.code());
                log.error("Production respondió {} {} a RELEASE del control {} del lote {}: la petición de Quality está mal (defecto, no se reintenta): {}",
                        defective.status(), defective.code(), controlId, control.batchId(), defective.message());
                yield ControlResolution.defect(control, defective.status(), defective.code(), defective.message());
            }
            case PhaseCommandOutcome.Unavailable unavailable -> {
                retries.unavailable(new PendingRetryScheduler.Target() {
                    @Override
                    public void recordFailedAttempt(int attempts, Instant nextAttemptAt, String error) {
                        controls.recordFailedAttempt(controlId, attempts, nextAttemptAt, error);
                    }

                    @Override
                    public void reschedule(Instant nextAttemptAt) {
                        controls.reschedule(controlId, nextAttemptAt);
                    }
                }, new PendingRetryScheduler.Subject("control", controlId.toString(), control.batchId().toString(), "IN_PROGRESS con la finalización pendiente",
                        "Finalización o descarte sin respuesta"), unavailable, attemptsSoFar);
                yield ControlResolution.pending(control);
            }
        };
    }

    /** Todo lo que cambia con la confirmación, en la misma transacción. */
    private ControlResolution complete(QualityControl control) {
        Instant now = clock.instant();
        UUID requester = control.finishRequestedBy();
        ControlOutcome outcome = control.completeFinish();
        BatchProjection batch = projections.findByIdForUpdate(control.batchId())
                .orElseThrow(() -> new IllegalStateException("El lote " + control.batchId() + " no está en batch_projection: no se puede aplicar el control"));
        QualityStatusChange change = batch.apply(outcome, now);
        projections.update(batch);
        FailureChange failureChange = FailureLifecycle.after(change.previous(), outcome,
                failures.findOpenByBatchForUpdate(control.batchId()).orElse(null), UUID.randomUUID());
        switch (failureChange.action()) {
            case GENERATE -> failures.insert(failureChange.failure(), now);
            case REOPEN, RESOLVE -> failures.save(failureChange.failure());
            case NONE -> { }
        }
        controls.save(control);

        if (failureChange.failure() != null) {
            events.publish(com.nodotextil.trazatex.quality.domain.event.QualityFailureUpdated.of(failureChange.failure()));
        }

        events.publish(QualityControlCompleted.of(outcome, control.tests()));
        if (change.changed()) {
            events.publish(QualityStatusChanged.of(control.batchId(), batch.companyId(), change, control.id()));
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("batchId", control.batchId().toString());
        changes.put("controlType", control.controlType().name());
        changes.put("result", outcome.overallResult().name());
        changes.put("qualityStatus", change.current().name());
        audit.record(requester, control.companyId(), CONTROL_FINISHED, "QualityControl", control.id().toString(), changes);
        switch (failureChange.action()) {
            case GENERATE -> audit.record(requester, control.companyId(), FAILURE_GENERATED, "QualityFailure", failureChange.failure().id().toString(),
                    Map.of("batchId", control.batchId().toString(), "controlId", control.id().toString()));
            case REOPEN -> audit.record(requester, control.companyId(), FAILURE_REOPENED, "QualityFailure", failureChange.failure().id().toString(),
                    Map.of("batchId", control.batchId().toString(), "controlId", control.id().toString()));
            case RESOLVE -> audit.record(requester, control.companyId(), FAILURE_RESOLVED, "QualityFailure", failureChange.failure().id().toString(),
                    Map.of("batchId", control.batchId().toString(), "controlId", control.id().toString()));
            case NONE -> { }
        }
        return ControlResolution.confirmed(control, failureChange.failure());
    }

    private void reject(QualityControl control, String code) {
        UUID requester = control.finishRequestedBy();
        control.abortFinish();
        controls.save(control);
        audit.record(requester, control.companyId(), CONTROL_FINISH_REJECTED, "QualityControl", control.id().toString(),
                Map.of("batchId", control.batchId().toString(), "reason", code));
    }
}
