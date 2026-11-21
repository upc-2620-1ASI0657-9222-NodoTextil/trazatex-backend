package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class BatchProjection {

    public static final long NO_VERSION = -1;

    public enum TransferEffect {
        MOVED,
        ALREADY_THERE,
        OWNER_MISMATCH
    }

    private final UUID batchId;
    private String traceabilityId;
    private UUID companyId;
    private BatchPhase phase;
    private boolean qualityHold;
    private QualityStatus qualityStatus;
    private boolean everControlled;
    private long batchVersion;
    private Instant updatedAt;

    private BatchProjection(UUID batchId, UUID companyId, BatchPhase phase, boolean qualityHold, QualityStatus qualityStatus,
                            boolean everControlled, long batchVersion, Instant updatedAt, String traceabilityId) {
        this.batchId = Objects.requireNonNull(batchId, "batchId");
        this.traceabilityId = traceabilityId;
        this.companyId = Objects.requireNonNull(companyId, "companyId");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.qualityHold = qualityHold;
        this.qualityStatus = qualityStatus;
        this.everControlled = everControlled;
        this.batchVersion = batchVersion;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static BatchProjection registered(UUID batchId, UUID companyId, Instant now) {
        return registered(batchId, companyId, null, now);
    }

    public static BatchProjection registered(UUID batchId, UUID companyId, String traceabilityId, Instant now) {
        return new BatchProjection(batchId, companyId, BatchPhase.AVAILABLE, false, QualityStatus.NOT_REVIEWED, false, NO_VERSION, now, traceabilityId);
    }

    public static BatchProjection splitChild(UUID batchId, UUID companyId, QualityStatus qualityStatus, Instant now) {
        return splitChild(batchId, companyId, qualityStatus, null, now);
    }

    public static BatchProjection splitChild(UUID batchId, UUID companyId, QualityStatus qualityStatus, String traceabilityId, Instant now) {
        Objects.requireNonNull(qualityStatus, "qualityStatus");
        return new BatchProjection(batchId, companyId, BatchPhase.AVAILABLE, false, qualityStatus,
                qualityStatus == QualityStatus.CONFORMING, NO_VERSION, now, traceabilityId);
    }

    public static BatchProjection partial(UUID batchId, UUID companyId, BatchPhase phase, boolean qualityHold, long batchVersion, Instant now) {
        return new BatchProjection(batchId, companyId, phase, qualityHold, null, false, batchVersion, now, null);
    }

    public static BatchProjection restore(UUID batchId, UUID companyId, BatchPhase phase, boolean qualityHold, QualityStatus qualityStatus,
                                          boolean everControlled, long batchVersion, Instant updatedAt) {
        return restore(batchId, companyId, phase, qualityHold, qualityStatus, everControlled, batchVersion, updatedAt, null);
    }

    public static BatchProjection restore(UUID batchId, UUID companyId, BatchPhase phase, boolean qualityHold, QualityStatus qualityStatus,
                                          boolean everControlled, long batchVersion, Instant updatedAt, String traceabilityId) {
        return new BatchProjection(batchId, companyId, phase, qualityHold, qualityStatus, everControlled, batchVersion, updatedAt, traceabilityId);
    }

    public boolean completeFromCreation(QualityStatus status, Instant now) {
        return completeFromCreation(status, null, now);
    }

    public boolean completeFromCreation(QualityStatus status, String newTraceabilityId, Instant now) {
        boolean changed = false;
        if (traceabilityId == null && newTraceabilityId != null) {
            traceabilityId = newTraceabilityId;
            changed = true;
        }
        if (qualityStatus == null) {
            qualityStatus = Objects.requireNonNull(status, "status");
            everControlled = status == QualityStatus.CONFORMING;
            changed = true;
        }
        if (changed) {
            updatedAt = now;
        }
        return changed;
    }

    public boolean applyPhaseChanged(UUID newCompanyId, BatchPhase newPhase, boolean newHold, long version, Instant now) {
        if (version <= batchVersion) {
            return false;
        }
        companyId = Objects.requireNonNull(newCompanyId, "companyId");
        phase = Objects.requireNonNull(newPhase, "phase");
        qualityHold = newHold;
        batchVersion = version;
        updatedAt = now;
        return true;
    }

    public TransferEffect applyTransferAccepted(UUID fromCompanyId, UUID toCompanyId, Instant now) {
        if (companyId.equals(toCompanyId)) {
            return TransferEffect.ALREADY_THERE;
        }
        if (!companyId.equals(fromCompanyId)) {
            return TransferEffect.OWNER_MISMATCH;
        }
        companyId = toCompanyId;
        updatedAt = now;
        return TransferEffect.MOVED;
    }

    public ControlType controlTypeToStart() {
        if (qualityStatus == null) {
            throw new ConflictException(ErrorCodes.BATCH_QUALITY_UNKNOWN,
                    "Todavía no se conoce el estado de calidad del lote " + batchId + ": espera a que llegue su registro.");
        }
        if (phase == BatchPhase.IN_QUALITY_CONTROL) {
            throw new ConflictException(ErrorCodes.CONTROL_ALREADY_ACTIVE, "El lote ya tiene un control de calidad activo.");
        }
        if (phase != BatchPhase.AVAILABLE) {
            throw new ConflictException(ErrorCodes.BATCH_NOT_AVAILABLE_FOR_CONTROL,
                    "Solo se puede controlar un lote disponible; este está en " + phase + ".");
        }
        return switch (qualityStatus) {
            case NOT_REVIEWED -> ControlType.EVALUATION;
            case PENDING_REEVALUATION -> ControlType.REEVALUATION;
            case POTENTIAL_DERIVED_FAILURE -> everControlled ? ControlType.REEVALUATION : ControlType.EVALUATION;
            case CONFORMING -> throw new ConflictException(ErrorCodes.BATCH_ALREADY_CONFORMING,
                    "El lote ya es conforme: no admite otro control.");
            case FAILED -> throw new ConflictException(ErrorCodes.BATCH_FAILED_REQUIRES_REEVALUATION,
                    "El lote tiene una falla: envíalo a reevaluación antes de iniciar un control.");
        };
    }

    public QualityStatusChange apply(ControlOutcome outcome, Instant now) {
        if (!batchId.equals(outcome.batchId())) {
            throw new ConflictException(ErrorCodes.CONTROL_BATCH_MISMATCH, "El control es de otro lote.");
        }
        requireStatusIn("aplicar el resultado de un control", QualityStatus.NOT_REVIEWED, QualityStatus.PENDING_REEVALUATION,
                QualityStatus.POTENTIAL_DERIVED_FAILURE);
        QualityStatus previous = qualityStatus;
        qualityStatus = outcome.newQualityStatus();
        qualityHold = outcome.qualityHold();
        everControlled = true;
        updatedAt = now;
        return new QualityStatusChange(previous, qualityStatus);
    }

    public QualityStatusChange sendToReevaluation(Instant now) {
        requireStatusIn("enviar a reevaluación", QualityStatus.FAILED);
        qualityStatus = QualityStatus.PENDING_REEVALUATION;
        updatedAt = now;
        return new QualityStatusChange(QualityStatus.FAILED, qualityStatus);
    }

    public void requireFailedToDiscard() {
        requireStatusIn("descartar", QualityStatus.FAILED);
    }

    public QualityStatusChange applyConfirmedDerivedHold(Instant now) {
        requireStatusIn("aplicar la retención por falla derivada", QualityStatus.NOT_REVIEWED, QualityStatus.CONFORMING);
        QualityStatus previous = qualityStatus;
        qualityStatus = QualityStatus.POTENTIAL_DERIVED_FAILURE;
        qualityHold = true;
        updatedAt = now;
        return new QualityStatusChange(previous, qualityStatus);
    }

    private void requireStatusIn(String operation, QualityStatus... allowed) {
        if (qualityStatus == null) {
            throw new ConflictException(ErrorCodes.BATCH_QUALITY_UNKNOWN, "Todavía no se conoce el estado de calidad del lote " + batchId + ".");
        }
        for (QualityStatus status : allowed) {
            if (qualityStatus == status) {
                return;
            }
        }
        throw new ConflictException(ErrorCodes.INVALID_QUALITY_TRANSITION,
                "No se puede " + operation + " con el lote en estado de calidad " + qualityStatus + ".");
    }

    public UUID batchId() {
        return batchId;
    }

    public String traceabilityId() {
        return traceabilityId;
    }

    public UUID companyId() {
        return companyId;
    }

    public BatchPhase phase() {
        return phase;
    }

    public boolean qualityHold() {
        return qualityHold;
    }

    public QualityStatus qualityStatus() {
        return qualityStatus;
    }

    public boolean everControlled() {
        return everControlled;
    }

    public long batchVersion() {
        return batchVersion;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
