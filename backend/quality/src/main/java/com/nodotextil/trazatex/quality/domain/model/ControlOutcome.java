package com.nodotextil.trazatex.quality.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * El resultado de un control <b>finalizado</b>. Solo lo produce {@link QualityControl#finish}: el constructor no es público, así que
 * lo que cambia el estado de calidad del lote ({@link BatchProjection#apply}) y {@code everControlled} viene siempre de un control
 * que llegó a {@code FINISHED}; ni iniciar ni cancelar un control pasan por aquí.
 */
public final class ControlOutcome {

    private final UUID controlId;
    private final UUID batchId;
    private final UUID companyId;
    private final ControlType controlType;
    private final TestResult overallResult;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final List<UUID> failedTestIds;

    ControlOutcome(UUID controlId, UUID batchId, UUID companyId, ControlType controlType, TestResult overallResult, Instant startedAt,
                   Instant finishedAt, List<UUID> failedTestIds) {
        this.controlId = controlId;
        this.batchId = batchId;
        this.companyId = companyId;
        this.controlType = controlType;
        this.overallResult = overallResult;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.failedTestIds = List.copyOf(failedTestIds);
    }

    public UUID controlId() {
        return controlId;
    }

    public UUID batchId() {
        return batchId;
    }

    public UUID companyId() {
        return companyId;
    }

    public ControlType controlType() {
        return controlType;
    }

    public TestResult overallResult() {
        return overallResult;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    /** Las pruebas fallidas: la falla que se genera queda ligada al lote, al control y a estas pruebas. */
    public List<UUID> failedTestIds() {
        return failedTestIds;
    }

    public boolean conforming() {
        return overallResult == TestResult.CONFORMING;
    }

    /** El estado de calidad en que deja al lote (US25, US28, US34). */
    public QualityStatus newQualityStatus() {
        return conforming() ? QualityStatus.CONFORMING : QualityStatus.FAILED;
    }

    /** {@code RELEASE} fija la retención en el mismo cambio: {@code false} si salió conforme, {@code true} si salió con falla. */
    public boolean qualityHold() {
        return !conforming();
    }
}
