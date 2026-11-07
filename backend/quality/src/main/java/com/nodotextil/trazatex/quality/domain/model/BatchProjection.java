package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Copia local del lote para Quality (docs/domain/quality.md): fase y retención las gobierna Production y llegan por eventos; el estado
 * de calidad y {@code everControlled} los gobierna Quality. Lo único que cambia {@code qualityStatus} después de que el lote nace es
 * un control finalizado ({@link #apply}), enviar una falla a reevaluación o aplicar una retención de falla derivada.
 *
 * <p><b>Sin orden entre tópicos.</b> {@code BatchRegistered}, {@code BatchSplit} y {@code TransformationCompleted} (que dan a
 * conocer el lote) y {@code BatchPhaseChanged} viajan en tópicos distintos, así que un {@code BatchPhaseChanged} puede llegar
 * primero: la fila nace <i>parcial</i> (sin {@code qualityStatus}) y el evento de nacimiento la completa sin pisar la fase ni la
 * retención. {@code BatchPhaseChanged} solo se aplica si trae una {@code batchVersion} mayor que la guardada.
 */
public final class BatchProjection {

    /** Aún no se aplicó ningún {@code BatchPhaseChanged}. */
    public static final long NO_VERSION = -1;

    /** Resultado de aplicar {@code TransferAccepted}. */
    public enum TransferEffect {
        /** El lote estaba con la empresa de origen y pasó a la destino. */
        MOVED,
        /** Ya estaba con la empresa destino (el {@code BatchPhaseChanged} de la aceptación llegó antes). */
        ALREADY_THERE,
        /** El lote no está con la empresa de origen ni con la destino (una transferencia posterior ya lo movió): se ignora. */
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

    /** Un lote recién registrado, o una salida de transformación: nace {@code AVAILABLE}, sin retención y {@code NOT_REVIEWED}. */
    public static BatchProjection registered(UUID batchId, UUID companyId, Instant now) {
        return registered(batchId, companyId, null, now);
    }

    public static BatchProjection registered(UUID batchId, UUID companyId, String traceabilityId, Instant now) {
        return new BatchProjection(batchId, companyId, BatchPhase.AVAILABLE, false, QualityStatus.NOT_REVIEWED, false, NO_VERSION, now, traceabilityId);
    }

    /**
     * Un hijo de una división: nace {@code AVAILABLE} y sin retención (solo se divide un lote habilitado) con el estado de calidad del
     * padre, que solo puede ser {@code NOT_REVIEWED} o {@code CONFORMING}. {@code everControlled} se deduce: un hijo ya conforme tuvo
     * un control.
     */
    public static BatchProjection splitChild(UUID batchId, UUID companyId, QualityStatus qualityStatus, Instant now) {
        return splitChild(batchId, companyId, qualityStatus, null, now);
    }

    public static BatchProjection splitChild(UUID batchId, UUID companyId, QualityStatus qualityStatus, String traceabilityId, Instant now) {
        Objects.requireNonNull(qualityStatus, "qualityStatus");
        return new BatchProjection(batchId, companyId, BatchPhase.AVAILABLE, false, qualityStatus,
                qualityStatus == QualityStatus.CONFORMING, NO_VERSION, now, traceabilityId);
    }

    /** Un lote que solo se conoce por su {@code BatchPhaseChanged}: falta su estado de calidad. */
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

    // ---- Eventos de Production ----

    /**
     * El evento que da a conocer el lote llegó y la fila ya existía (nació de un {@code BatchPhaseChanged}): completa el estado de
     * calidad sin tocar fase, retención ni empresa. Si la fila ya lo tenía no hace nada.
     *
     * @return si cambió algo
     */
    public boolean completeFromCreation(QualityStatus status, Instant now) {
        return completeFromCreation(status, null, now);
    }

    /** Además completa el Traceability ID si la fila no lo tenía (filas anteriores a V4, o nacidas de un {@code BatchPhaseChanged}). */
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

    /**
     * {@code BatchPhaseChanged}: copia empresa, fase y retención <b>posteriores</b> al cambio, solo si su versión es mayor que la
     * guardada (un evento viejo, repetido o fuera de orden no retrocede el lote).
     *
     * @return si se aplicó
     */
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

    /**
     * {@code TransferAccepted}: la empresa responsable pasa a la destino. Solo si el lote sigue con la de origen: una transferencia
     * posterior (otro tópico, sin orden) o el {@code BatchPhaseChanged} de la propia aceptación ya pudieron moverlo.
     */
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

    // ---- Reglas de Quality ----

    /**
     * El tipo de control que corresponde (US23, US34) o el motivo por el que el lote no puede controlarse:
     * <ul>
     *   <li>{@code NOT_REVIEWED} → {@code EVALUATION}; {@code PENDING_REEVALUATION} → {@code REEVALUATION};</li>
     *   <li>{@code POTENTIAL_DERIVED_FAILURE} → {@code EVALUATION} si nunca tuvo un control terminado, {@code REEVALUATION} si sí;</li>
     *   <li>{@code CONFORMING} y {@code FAILED} se rechazan (un lote con falla se reevalúa primero, US26).</li>
     * </ul>
     * Y la fase debe ser {@code AVAILABLE}: {@code IN_QUALITY_CONTROL} es que ya tiene un control activo (US23 esc. 3).
     */
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

    /**
     * Aplica el resultado de un control <b>finalizado</b>: el lote queda {@code CONFORMING} (sin retención) o {@code FAILED} (retenido)
     * y {@code everControlled} pasa a {@code true}. Es el único lugar donde {@code everControlled} se vuelve {@code true}: iniciar o
     * cancelar un control no lo toca.
     */
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

    /** US26: un lote con falla pasa a {@code PENDING_REEVALUATION} y sigue retenido. */
    public QualityStatusChange sendToReevaluation(Instant now) {
        requireStatusIn("enviar a reevaluación", QualityStatus.FAILED);
        qualityStatus = QualityStatus.PENDING_REEVALUATION;
        updatedAt = now;
        return new QualityStatusChange(QualityStatus.FAILED, qualityStatus);
    }

    /** US27: solo se descarta un lote con falla. */
    public void requireFailedToDiscard() {
        requireStatusIn("descartar", QualityStatus.FAILED);
    }

    /**
     * US34: Production <b>confirmó</b> el {@code HOLD}; recién entonces el lote pasa a {@code POTENTIAL_DERIVED_FAILURE}, que implica
     * {@code qualityHold = true}. Solo desde {@code NOT_REVIEWED} o {@code CONFORMING}: los demás estados conservan el suyo.
     */
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

    // ---- Lectura ----

    public UUID batchId() {
        return batchId;
    }

    /** El Traceability ID del lote; {@code null} en las filas anteriores a V4 que aún no recibieron su evento de nacimiento. */
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

    /** {@code null} mientras la fila solo se conoce por un {@code BatchPhaseChanged}. */
    public QualityStatus qualityStatus() {
        return qualityStatus;
    }

    /** Si algún control del lote llegó a {@code FINISHED}. */
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
