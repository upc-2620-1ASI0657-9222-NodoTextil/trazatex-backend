package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;

import java.util.UUID;

/**
 * Un lote que Traceability marcó como posible falla derivada de una falla confirmada ({@code PossibleDerivedFailureDetected}). Se
 * registra siempre, para no perder qué falla señaló al lote. {@code impactType} se deduce del antecesor común
 * ({@link ImpactType#derive}); {@code lockStatus} nace según {@link DerivedFailurePolicy} y solo puede pasar de {@code PENDING} a
 * {@code APPLIED} o {@code NO_ACTION}.
 */
public final class ImpactedBatch {

    private final UUID failureId;
    private final UUID batchId;
    private final ImpactType impactType;
    private LockStatus lockStatus;

    private ImpactedBatch(UUID failureId, UUID batchId, ImpactType impactType, LockStatus lockStatus) {
        this.failureId = failureId;
        this.batchId = batchId;
        this.impactType = impactType;
        this.lockStatus = lockStatus;
    }

    public static ImpactedBatch detect(UUID failureId, UUID sourceBatchId, UUID batchId, UUID commonAncestorBatchId, DerivedFailureDecision decision) {
        return new ImpactedBatch(failureId, batchId, ImpactType.derive(sourceBatchId, batchId, commonAncestorBatchId), decision.initialLockStatus());
    }

    public static ImpactedBatch restore(UUID failureId, UUID batchId, ImpactType impactType, LockStatus lockStatus) {
        return new ImpactedBatch(failureId, batchId, impactType, lockStatus);
    }

    /** Production confirmó el {@code HOLD}. */
    public void markApplied() {
        requirePending("marcar como aplicada");
        lockStatus = LockStatus.APPLIED;
    }

    /** Al reevaluar el aviso resultó que no hay nada que hacer (lote ya retenido o histórico). */
    public void markNoAction() {
        requirePending("marcar sin acción");
        lockStatus = LockStatus.NO_ACTION;
    }

    private void requirePending(String operation) {
        if (lockStatus != LockStatus.PENDING) {
            throw new ConflictException(ErrorCodes.INVALID_LOCK_TRANSITION, "No se puede " + operation + " un aviso en estado " + lockStatus + ".");
        }
    }

    public UUID failureId() {
        return failureId;
    }

    public UUID batchId() {
        return batchId;
    }

    public ImpactType impactType() {
        return impactType;
    }

    public LockStatus lockStatus() {
        return lockStatus;
    }
}
