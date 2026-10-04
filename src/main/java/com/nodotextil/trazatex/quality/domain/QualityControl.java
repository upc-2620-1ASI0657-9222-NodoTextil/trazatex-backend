package com.nodotextil.trazatex.quality.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class QualityControl {

    private final UUID id;
    private final UUID batchId;
    private final ControlType type;
    private final LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private final List<QualityTest> tests;

    public QualityControl(
            UUID id,
            UUID batchId,
            ControlType type,
            LocalDateTime startedAt) {

        this(id, batchId, type, startedAt, null, List.of());
    }

    private QualityControl(
            UUID id,
            UUID batchId,
            ControlType type,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            List<QualityTest> tests) {

        this.id = Objects.requireNonNull(id, "Control id is required");
        this.batchId = Objects.requireNonNull(batchId, "Batch id is required");
        this.type = Objects.requireNonNull(type, "Control type is required");
        this.startedAt = Objects.requireNonNull(startedAt, "Control start date is required");
        this.completedAt = completedAt;
        this.tests = new ArrayList<>(Objects.requireNonNull(tests, "Tests are required"));
    }

    public static QualityControl restore(
            UUID id,
            UUID batchId,
            ControlType type,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            List<QualityTest> tests) {

        return new QualityControl(id, batchId, type, startedAt, completedAt, tests);
    }

    public void addTest(QualityTest test) {
        Objects.requireNonNull(test, "Quality test is required");

        if (isCompleted()) {
            throw new InvalidQualityControlException(
                    "Tests cannot be added to a completed quality control"
            );
        }

        tests.add(test);
    }

    public void complete(LocalDateTime completionDate) {
        Objects.requireNonNull(completionDate, "Control completion date is required");

        if (isCompleted()) {
            throw new InvalidQualityControlException(
                    "Quality control is already completed"
            );
        }

        if (tests.isEmpty()) {
            throw new InvalidQualityControlException(
                    "A quality control requires at least one test before completion"
            );
        }

        if (completionDate.isBefore(startedAt)) {
            throw new InvalidQualityControlException(
                    "Control completion date cannot be before its start date"
            );
        }

        this.completedAt = completionDate;
    }

    public boolean hasFailedTests() {
        return tests.stream()
                .anyMatch(test -> test.result() == TestResult.FAILED);
    }

    public List<UUID> getFailedTestIds() {
        return tests.stream()
                .filter(test -> test.result() == TestResult.FAILED)
                .map(QualityTest::id)
                .toList();
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public UUID getId() { return id; }
    public UUID getBatchId() { return batchId; }
    public ControlType getType() { return type; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public List<QualityTest> getTests() { return List.copyOf(tests); }
}
