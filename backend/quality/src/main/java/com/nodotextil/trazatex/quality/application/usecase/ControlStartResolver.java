package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.quality.application.port.out.PhaseCommand;
import com.nodotextil.trazatex.quality.application.port.out.PhaseCommandOutcome;
import com.nodotextil.trazatex.quality.application.port.out.ProductionPhasePort;
import com.nodotextil.trazatex.quality.application.port.out.QualityControlRepositoryPort;
import com.nodotextil.trazatex.quality.domain.model.ControlStatus;
import com.nodotextil.trazatex.quality.domain.model.QualityControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;

@Component
class ControlStartResolver {

    private static final Logger log = LoggerFactory.getLogger(ControlStartResolver.class);

    static final String CONTROL_CANCELLED = "CONTROL_CANCELLED";

    private final ProductionPhasePort production;
    private final QualityControlRepositoryPort controls;
    private final AuditLogPort audit;
    private final PendingRetryScheduler retries;
    private final TransactionTemplate requiresNew;

    ControlStartResolver(ProductionPhasePort production, QualityControlRepositoryPort controls, AuditLogPort audit, PendingRetryScheduler retries,
                         PlatformTransactionManager transactionManager) {
        this.production = production;
        this.controls = controls;
        this.audit = audit;
        this.retries = retries;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    ControlResolution resolve(QualityControl control, int attemptsSoFar) {
        PhaseCommandOutcome outcome = production.send(PhaseCommand.startEvaluation(control.batchId(), control.id(), control.inspectorId()));
        return requiresNew.execute(tx -> apply(control.id(), outcome, attemptsSoFar));
    }

    private ControlResolution apply(java.util.UUID controlId, PhaseCommandOutcome outcome, int attemptsSoFar) {
        QualityControl control = controls.findByIdForUpdate(controlId)
                .orElseThrow(() -> new IllegalStateException("El control " + controlId + " desapareció mientras se resolvía"));
        if (control.status() != ControlStatus.STARTING) {
            log.debug("El control {} ya estaba {}: otro lo resolvió", controlId, control.status());
            return ControlResolution.alreadyResolved(control);
        }
        return switch (outcome) {
            case PhaseCommandOutcome.Confirmed confirmed -> {
                control.confirmStarted();
                controls.save(control);
                yield ControlResolution.confirmed(control);
            }
            case PhaseCommandOutcome.Rejected rejected -> {
                cancel(control, rejected.code());
                log.info("Production rechazó iniciar el control {} del lote {} ({} {}): se cancela", controlId, control.batchId(), rejected.status(),
                        rejected.code());
                yield ControlResolution.rejected(control, rejected.status(), rejected.code(), rejected.message());
            }
            case PhaseCommandOutcome.Defective defective -> {
                cancel(control, defective.code());
                log.error("Production respondió {} {} al inicio del control {} del lote {}: la petición de Quality está mal (defecto, no se reintenta): {}",
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
                }, new PendingRetryScheduler.Subject("control", controlId.toString(), control.batchId().toString(), "STARTING",
                        "Control atascado en STARTING"), unavailable, attemptsSoFar);
                yield ControlResolution.pending(control);
            }
        };
    }

    private void cancel(QualityControl control, String reason) {
        control.cancel(reason);
        controls.save(control);
        audit.record(control.inspectorId(), control.companyId(), CONTROL_CANCELLED, "QualityControl", control.id().toString(),
                Map.of("batchId", control.batchId().toString(), "reason", reason));
    }
}
