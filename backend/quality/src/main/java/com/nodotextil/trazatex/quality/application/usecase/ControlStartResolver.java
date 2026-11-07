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

/**
 * Resuelve un control {@code STARTING}: manda {@code START_EVALUATION} a Production <b>fuera de toda transacción</b> y aplica la respuesta
 * en una transacción propia ({@code REQUIRES_NEW}, la «B»). Lo usan la petición del usuario y el job de recuperación, con el mismo
 * {@code requestId = controlId}: Production es idempotente, así que repetirlo es seguro aunque la primera vez se haya aplicado y la
 * respuesta se haya perdido o Quality haya caído antes de guardarla.
 *
 * <p>Reacción a cada respuesta (docs/architecture/communication.md): 200 → {@code IN_PROGRESS}; 409 o 404 → {@code CANCELLED} con el código de
 * Production como motivo; 400 (u otro 4xx) → {@code CANCELLED} con su código y un ERROR (ampliación acordada con el usuario: sin ella el
 * control quedaría {@code STARTING} para siempre bloqueando el lote); 503, otro 5xx, timeout, sin respuesta o circuito abierto → el control
 * sigue {@code STARTING} y se reprograma. <b>Nunca se cancela por duda</b>: sin respuesta Production pudo haber aplicado el comando.
 */
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

    /**
     * @param attemptsSoFar intentos sin respuesta que ya lleva el control (0 en la petición que lo creó)
     */
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

    /** CONTROL_CANCELLED en la misma transacción; el usuario es el del control (el solicitante, también cuando lo aplica el job). */
    private void cancel(QualityControl control, String reason) {
        control.cancel(reason);
        controls.save(control);
        audit.record(control.inspectorId(), control.companyId(), CONTROL_CANCELLED, "QualityControl", control.id().toString(),
                Map.of("batchId", control.batchId().toString(), "reason", reason));
    }
}
