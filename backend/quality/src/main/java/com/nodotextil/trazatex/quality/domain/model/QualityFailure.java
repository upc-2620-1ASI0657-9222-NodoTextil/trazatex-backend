package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * La falla que genera un control con pruebas fallidas (US25, US26, US27, US28). {@code OPEN} es «generada, sin decisión final»; se
 * queda {@code OPEN} mientras el lote se reevalúa. {@code controlId} apunta al <b>último</b> control que la originó o reabrió; las
 * pruebas de los controles anteriores siguen ligadas a su propio control.
 *
 * <pre>
 *  OPEN ──confirm──▶ CONFIRMED          (se descarta el lote)
 *  OPEN ──resolve──▶ RESOLVED           (la reevaluación salió conforme)
 * </pre>
 */
public final class QualityFailure {

    private final UUID id;
    private UUID controlId;
    private final UUID batchId;
    private final UUID companyId;
    private String rootCause;
    private BigDecimal affectedQtyKg;
    private String observations;
    private FailureStatus status;
    private Instant discardRequestedAt;
    private UUID discardRequestedBy;

    private QualityFailure(UUID id, UUID controlId, UUID batchId, UUID companyId, String rootCause, BigDecimal affectedQtyKg, String observations,
                           FailureStatus status) {
        this.id = id;
        this.controlId = controlId;
        this.batchId = batchId;
        this.companyId = companyId;
        this.rootCause = rootCause;
        this.affectedQtyKg = affectedQtyKg;
        this.observations = observations;
        this.status = status;
    }

    /** US25 esc. 2: un control con falla genera su falla, {@code OPEN}, ligada al lote y al control. */
    public static QualityFailure generate(UUID id, ControlOutcome outcome) {
        if (id == null) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS, "Falta el id de la falla", List.of("id"));
        }
        if (outcome.conforming()) {
            throw new ConflictException(ErrorCodes.INVALID_FAILURE_TRANSITION, "Un control conforme no genera una falla.");
        }
        return new QualityFailure(id, outcome.controlId(), outcome.batchId(), outcome.companyId(), null, null, null, FailureStatus.OPEN);
    }

    public static QualityFailure restore(UUID id, UUID controlId, UUID batchId, String rootCause, BigDecimal affectedQtyKg, String observations,
                                         FailureStatus status) {
        return new QualityFailure(id, controlId, batchId, null, rootCause, affectedQtyKg, observations, status);
    }

    public static QualityFailure restore(UUID id, UUID controlId, UUID batchId, UUID companyId, String rootCause, BigDecimal affectedQtyKg,
                                         String observations, FailureStatus status, Instant discardRequestedAt, UUID discardRequestedBy) {
        QualityFailure failure = new QualityFailure(id, controlId, batchId, companyId, rootCause, affectedQtyKg, observations, status);
        failure.discardRequestedAt = discardRequestedAt;
        failure.discardRequestedBy = discardRequestedBy;
        return failure;
    }

    /** US26 esc. 1: causa, cantidad afectada (mayor que cero) y observaciones. Se puede corregir mientras esté {@code OPEN}. */
    public void complete(String rootCause, BigDecimal affectedQtyKg, String observations) {
        requireOpen("completar");
        requireNoDiscardPending();
        String cause = rootCause == null || rootCause.isBlank() ? null : rootCause.trim();
        if (cause == null) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS, "Falta la causa de la falla", List.of("rootCause"));
        }
        if (affectedQtyKg == null) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS, "Falta la cantidad afectada", List.of("affectedQtyKg"));
        }
        if (affectedQtyKg.signum() <= 0) {
            throw new ValidationException(ErrorCodes.INVALID_QUANTITY, "La cantidad afectada debe ser mayor que cero", List.of("affectedQtyKg"));
        }
        this.rootCause = cause;
        this.affectedQtyKg = affectedQtyKg;
        this.observations = observations == null || observations.isBlank() ? null : observations.trim();
    }

    /** US26 esc. 2: la falla debe seguir abierta para reevaluar el lote; no cambia de estado (sigue {@code OPEN}). */
    public void startReevaluation() {
        requireOpen("enviar a reevaluación");
        requireNoDiscardPending();
    }

    /**
     * US27: pide descartar el lote. Exige causa no vacía y cantidad afectada mayor que cero —{@code FailureConfirmed} las lleva como
     * obligatorias y Analytics calcula las causas más frecuentes desde la causa—; enviar a reevaluación no exige nada. La falla sigue
     * {@code OPEN} (y deja de admitir cambios) hasta que Production confirme {@code DISCARD} ({@link #completeDiscard}).
     *
     * @return {@code true} si la solicitud es nueva, {@code false} si ya estaba pedida
     */
    public boolean requestDiscard(UUID requestedBy, Instant now) {
        requireOpen("descartar");
        requireInfoComplete();
        if (discardPending()) {
            return false;
        }
        if (requestedBy == null) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS, "Falta quién descarta el lote", List.of("requestedBy"));
        }
        discardRequestedAt = now;
        discardRequestedBy = requestedBy;
        return true;
    }

    /** Production confirmó {@code DISCARD}: la falla queda {@code CONFIRMED} (ya no cambia). */
    public void completeDiscard() {
        requireOpen("confirmar");
        requireInfoComplete();
        if (!discardPending()) {
            throw new ConflictException(ErrorCodes.INVALID_FAILURE_TRANSITION, "La falla no tiene un descarte pendiente.");
        }
        status = FailureStatus.CONFIRMED;
    }

    /** Production rechazó {@code DISCARD} (409, 404 o un 400): la falla sigue {@code OPEN} y vuelve a admitir cambios. */
    public void abortDiscard() {
        if (!discardPending()) {
            throw new ConflictException(ErrorCodes.INVALID_FAILURE_TRANSITION, "La falla no tiene un descarte pendiente.");
        }
        discardRequestedAt = null;
        discardRequestedBy = null;
    }

    public boolean discardPending() {
        return status == FailureStatus.OPEN && discardRequestedAt != null;
    }

    private void requireInfoComplete() {
        if (rootCause == null || rootCause.isBlank() || affectedQtyKg == null || affectedQtyKg.signum() <= 0) {
            throw new ConflictException(ErrorCodes.FAILURE_INFO_INCOMPLETE,
                    "Para descartar el lote registra primero la causa de la falla y la cantidad afectada.");
        }
    }

    private void requireNoDiscardPending() {
        if (discardPending()) {
            throw new ConflictException(ErrorCodes.FAILURE_DISCARD_PENDING,
                    "El lote se está descartando: la falla no admite otro cambio hasta que Production confirme.");
        }
    }

    /** US28 esc. 1: la reevaluación salió conforme. */
    public void resolve(ControlOutcome outcome) {
        requireOpen("resolver");
        requireSameBatch(outcome);
        if (!outcome.conforming()) {
            throw new ConflictException(ErrorCodes.INVALID_FAILURE_TRANSITION, "Solo un control conforme resuelve una falla.");
        }
        status = FailureStatus.RESOLVED;
    }

    /** US28 esc. 2: la reevaluación volvió a fallar. Se reutiliza esta falla (sigue {@code OPEN}) y apunta al control nuevo. */
    public void reopenAfterFailedReevaluation(ControlOutcome outcome) {
        requireOpen("reabrir");
        requireSameBatch(outcome);
        if (outcome.conforming()) {
            throw new ConflictException(ErrorCodes.INVALID_FAILURE_TRANSITION, "Un control conforme no reabre una falla.");
        }
        controlId = outcome.controlId();
        status = FailureStatus.OPEN;
    }

    private void requireOpen(String operation) {
        if (status != FailureStatus.OPEN) {
            throw new ConflictException(ErrorCodes.INVALID_FAILURE_TRANSITION,
                    "No se puede " + operation + " una falla en estado " + status + ".");
        }
    }

    private void requireSameBatch(ControlOutcome outcome) {
        if (!batchId.equals(outcome.batchId())) {
            throw new ConflictException(ErrorCodes.CONTROL_BATCH_MISMATCH, "El control es de otro lote.");
        }
    }

    public UUID id() {
        return id;
    }

    public UUID controlId() {
        return controlId;
    }

    public UUID batchId() {
        return batchId;
    }

    /** La empresa responsable del lote al generarse la falla; viaja en {@code FailureConfirmed}. */
    public UUID companyId() {
        return companyId;
    }

    public Instant discardRequestedAt() {
        return discardRequestedAt;
    }

    public UUID discardRequestedBy() {
        return discardRequestedBy;
    }

    public String rootCause() {
        return rootCause;
    }

    public BigDecimal affectedQtyKg() {
        return affectedQtyKg;
    }

    public String observations() {
        return observations;
    }

    public FailureStatus status() {
        return status;
    }
}
