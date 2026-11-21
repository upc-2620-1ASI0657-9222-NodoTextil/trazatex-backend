package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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

    public boolean isEnabled() {
        return phase() == BatchPhase.AVAILABLE && !qualityHold && !finalProduct;
    }

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

    public void ensurePhase(BatchPhase expected) {
        if (phase() != expected) {
            throw new ConflictException(ErrorCodes.INVALID_PHASE_TRANSITION,
                    "El lote " + traceabilityId + " está en fase \"" + phase().label() + "\" y se esperaba \""
                            + expected.label() + "\".");
        }
    }

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

    public void acceptTransfer(UUID targetCompanyId) {
        BatchPhase next = state.acceptTransfer(this);
        change(next, qualityHold, Objects.requireNonNull(targetCompanyId, "targetCompanyId"));
    }

    public void rejectTransfer() {
        change(state.rejectTransfer(this), qualityHold, companyId);
    }

    public void startEvaluation() {
        change(state.startEvaluation(this), qualityHold, companyId);
    }

    public void release(boolean qualityHold) {
        change(state.release(this), qualityHold, companyId);
    }

    public void discard() {
        change(state.discard(this), qualityHold, companyId);
    }

    public boolean hold() {
        state.ensureCanChangeQualityHold(this);
        return change(phase(), true, companyId);
    }

    public boolean releaseHold() {
        state.ensureCanChangeQualityHold(this);
        return change(phase(), false, companyId);
    }

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

    public List<BatchPhaseChange> drainPhaseChanges() {
        List<BatchPhaseChange> drained = List.copyOf(phaseChanges);
        phaseChanges.clear();
        return drained;
    }

    public boolean hasPhaseChanges() {
        return !phaseChanges.isEmpty();
    }

    public void showQualityStatus(QualityStatus status) {
        this.qualityStatus = Objects.requireNonNull(status, "status");
    }

    void markAsFinalProduct(CommercialData data) {
        this.finalProduct = true;
        this.commercialData = data == null ? CommercialData.EMPTY : data;
    }

    public void updateCommercialData(CommercialData data) {
        if (!finalProduct) {
            throw new ConflictException(ErrorCodes.NOT_A_FINAL_PRODUCT,
                    "Solo un producto final admite datos comerciales.");
        }
        this.commercialData = Objects.requireNonNull(data, "data");
    }

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
