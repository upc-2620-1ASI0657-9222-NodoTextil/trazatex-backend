package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lote de material (US12). Production es la única autoridad sobre su fase operativa y su retención por calidad.
 * Un lote nuevo solo se crea con {@link BatchFactory}, que le da Traceability ID y QR nuevos; este agregado se limita a
 * sus transiciones, que delegan en {@link BatchPhaseState} (patrón State).
 *
 * <p><b>Habilitado</b> (glosario, RF-024): {@code AVAILABLE}, sin retención por calidad y que no sea producto final.
 * Que la empresa responsable esté {@code ACTIVE} (RF-003) no lo sabe el lote: lo exige la capa de aplicación con la
 * proyección de empresas antes de cualquier operación productiva.
 *
 * <p>{@code version} es del bloqueo optimista: lo gestiona la persistencia, el dominio solo la conserva.
 */
public final class Batch {

    private final UUID id;
    private final TraceabilityId traceabilityId;
    private final QrCode qrCode;
    private UUID companyId;
    private final String name;
    private final String materialType;
    private final Quantity quantity;
    private final Composition composition;
    private BatchPhaseState state;
    private QualityStatus qualityStatus;
    private boolean qualityHold;
    private boolean finalProduct;
    private final BatchOriginType originType;
    private final String origin;
    private final String supplierName;
    private final String receptionCharacteristics;
    private final UUID createdBy;
    private final Instant createdAt;
    private CommercialData commercialData;
    private final long version;
    /** Cambios de fase o retención desde que se leyó el lote; los entrega {@link #drainPhaseChanges()}. */
    private final List<BatchPhaseChange> phaseChanges = new ArrayList<>();

    Batch(BatchSnapshot s) {
        this.id = Objects.requireNonNull(s.id(), "id");
        this.traceabilityId = Objects.requireNonNull(s.traceabilityId(), "traceabilityId");
        this.qrCode = Objects.requireNonNull(s.qrCode(), "qrCode");
        this.companyId = Objects.requireNonNull(s.companyId(), "companyId");
        this.name = s.name();
        this.materialType = Objects.requireNonNull(s.materialType(), "materialType");
        this.quantity = Objects.requireNonNull(s.quantity(), "quantity");
        this.composition = Objects.requireNonNull(s.composition(), "composition");
        this.state = BatchPhaseState.of(Objects.requireNonNull(s.phase(), "phase"));
        this.qualityStatus = Objects.requireNonNull(s.qualityStatus(), "qualityStatus");
        this.qualityHold = s.qualityHold();
        this.finalProduct = s.finalProduct();
        this.originType = Objects.requireNonNull(s.originType(), "originType");
        this.origin = s.origin();
        this.supplierName = s.supplierName();
        this.receptionCharacteristics = s.receptionCharacteristics();
        this.createdBy = s.createdBy();
        this.createdAt = s.createdAt();
        this.commercialData = s.commercialData() == null ? CommercialData.EMPTY : s.commercialData();
        this.version = s.version();
    }

    public static Batch restore(BatchSnapshot snapshot) {
        return new Batch(snapshot);
    }

    // --- Habilitado ---------------------------------------------------------------------------------------------

    /** Habilitado para dividir, transformar o transferir (sin contar la empresa; ver la nota de la clase). */
    public boolean isEnabled() {
        return phase() == BatchPhase.AVAILABLE && !qualityHold && !finalProduct;
    }

    /** 409 si el lote no está habilitado, con el motivo concreto. */
    public void ensureEnabled() {
        ensureNotFinalProduct();
        if (phase() != BatchPhase.AVAILABLE) {
            throw new ConflictException(ErrorCodes.INVALID_PHASE_TRANSITION,
                    "El lote " + traceabilityId + " no está disponible (fase \"" + phase().label() + "\").");
        }
        if (qualityHold) {
            throw new ConflictException(ErrorCodes.BATCH_ON_QUALITY_HOLD,
                    "El lote " + traceabilityId + " está retenido por calidad.");
        }
    }

    void ensureNotFinalProduct() {
        if (finalProduct) {
            throw new ConflictException(ErrorCodes.BATCH_IS_FINAL_PRODUCT,
                    "El lote " + traceabilityId + " es un producto final: no participa en nuevas operaciones.");
        }
    }

    /** 409 si el lote no está en la fase esperada; sirve para validar varios lotes antes de cambiar alguno. */
    public void ensurePhase(BatchPhase expected) {
        if (phase() != expected) {
            throw new ConflictException(ErrorCodes.INVALID_PHASE_TRANSITION,
                    "El lote " + traceabilityId + " está en fase \"" + phase().label() + "\" y se esperaba \""
                            + expected.label() + "\".");
        }
    }

    // --- Transiciones de fase -----------------------------------------------------------------------------------

    /** Padre de una división → {@code SPLIT}. */
    public void markSplit() {
        change(state.split(this), qualityHold, companyId);
    }

    public void startTransformation() {
        change(state.startTransformation(this), qualityHold, companyId);
    }

    public void finishTransformation() {
        change(state.finishTransformation(this), qualityHold, companyId);
    }

    public void startTransfer() {
        change(state.startTransfer(this), qualityHold, companyId);
    }

    /** Acepta la transferencia: pasa a la empresa destino, {@code AVAILABLE}, con el mismo Traceability ID y QR. */
    public void acceptTransfer(UUID targetCompanyId) {
        BatchPhase next = state.acceptTransfer(this);
        change(next, qualityHold, Objects.requireNonNull(targetCompanyId, "targetCompanyId"));
    }

    /** Rechaza la transferencia: vuelve {@code AVAILABLE} a la empresa de origen (que nunca dejó de figurar). */
    public void rejectTransfer() {
        change(state.rejectTransfer(this), qualityHold, companyId);
    }

    // --- Comandos de Quality (ADR-009) --------------------------------------------------------------------------

    /** {@code START_EVALUATION}. */
    public void startEvaluation() {
        change(state.startEvaluation(this), qualityHold, companyId);
    }

    /** {@code RELEASE}: vuelve a {@code AVAILABLE} y fija la retención en el mismo cambio. */
    public void release(boolean qualityHold) {
        change(state.release(this), qualityHold, companyId);
    }

    /** {@code DISCARD}. */
    public void discard() {
        change(state.discard(this), qualityHold, companyId);
    }

    /** {@code HOLD}: devuelve {@code false} si el lote ya estaba retenido (no cambia nada). */
    public boolean hold() {
        state.ensureCanChangeQualityHold(this);
        return change(phase(), true, companyId);
    }

    /** {@code RELEASE_HOLD}: devuelve {@code false} si el lote no estaba retenido. */
    public boolean releaseHold() {
        state.ensureCanChangeQualityHold(this);
        return change(phase(), false, companyId);
    }

    /**
     * Único punto donde cambian la fase, la retención y la empresa del lote. Si cambia la fase o la retención anota un
     * {@link BatchPhaseChange}; si no cambia nada, no anota nada (p. ej. {@code HOLD} sobre un lote ya retenido).
     *
     * @return {@code true} si la fase o la retención cambiaron
     */
    private boolean change(BatchPhase nextPhase, boolean nextHold, UUID nextCompanyId) {
        BatchPhase previousPhase = phase();
        boolean changed = previousPhase != nextPhase || qualityHold != nextHold;
        state = BatchPhaseState.of(nextPhase);
        qualityHold = nextHold;
        companyId = nextCompanyId;
        if (changed) {
            phaseChanges.add(new BatchPhaseChange(id, nextCompanyId, previousPhase, nextPhase, nextHold));
        }
        return changed;
    }

    /** Entrega los cambios de fase o retención anotados desde la última vez y los olvida: cada cambio se publica una vez. */
    public List<BatchPhaseChange> drainPhaseChanges() {
        List<BatchPhaseChange> drained = List.copyOf(phaseChanges);
        phaseChanges.clear();
        return drained;
    }

    /** ¿Hay cambios de fase o retención sin entregar? */
    public boolean hasPhaseChanges() {
        return !phaseChanges.isEmpty();
    }

    /** Copia de {@code QualityStatusChanged}, solo para mostrar; no toca {@code qualityHold}. */
    public void showQualityStatus(QualityStatus status) {
        this.qualityStatus = Objects.requireNonNull(status, "status");
    }

    // --- Producto final y datos comerciales ---------------------------------------------------------------------

    /** Solo al nacer una salida de transformación (US20 esc. 1). */
    void markAsFinalProduct(CommercialData data) {
        this.finalProduct = true;
        this.commercialData = data == null ? CommercialData.EMPTY : data;
    }

    /** US20 esc. 2 y 3: edita los datos comerciales de un producto final (siempre privados). */
    public void updateCommercialData(CommercialData data) {
        if (!finalProduct) {
            throw new ConflictException(ErrorCodes.NOT_A_FINAL_PRODUCT,
                    "Solo un producto final admite datos comerciales.");
        }
        this.commercialData = Objects.requireNonNull(data, "data");
    }

    // --- Lectura ------------------------------------------------------------------------------------------------

    public UUID id() {
        return id;
    }

    public TraceabilityId traceabilityId() {
        return traceabilityId;
    }

    public QrCode qrCode() {
        return qrCode;
    }

    public UUID companyId() {
        return companyId;
    }

    public String name() {
        return name;
    }

    public String materialType() {
        return materialType;
    }

    public Quantity quantity() {
        return quantity;
    }

    public Composition composition() {
        return composition;
    }

    public BatchPhase phase() {
        return state.phase();
    }

    public QualityStatus qualityStatus() {
        return qualityStatus;
    }

    public boolean qualityHold() {
        return qualityHold;
    }

    public boolean isFinalProduct() {
        return finalProduct;
    }

    public BatchOriginType originType() {
        return originType;
    }

    public String origin() {
        return origin;
    }

    public String supplierName() {
        return supplierName;
    }

    public String receptionCharacteristics() {
        return receptionCharacteristics;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public CommercialData commercialData() {
        return commercialData;
    }

    public long version() {
        return version;
    }
}
