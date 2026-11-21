package com.nodotextil.trazatex.quality.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    public List<UUID> failedTestIds() {
        return failedTestIds;
    }

    public boolean conforming() {
        return overallResult == TestResult.CONFORMING;
    }

    public QualityStatus newQualityStatus() {
        return conforming() ? QualityStatus.CONFORMING : QualityStatus.FAILED;
    }

    public boolean qualityHold() {
        return !conforming();
    }
}
