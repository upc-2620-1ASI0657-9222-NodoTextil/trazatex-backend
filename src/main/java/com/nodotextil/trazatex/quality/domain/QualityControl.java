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

        this.id = Objects.requireNonNull(id, "Control id is required");
        this.batchId = Objects.requireNonNull(batchId, "Batch id is required");
        this.type = Objects.requireNonNull(type, "Control type is required");
        this.startedAt = Objects.requireNonNull(startedAt, "Control start date is required");
        this.tests = new ArrayList<>();
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

    public boolean isCompleted() {
        return completedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public ControlType getType() {
        return type;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public List<QualityTest> getTests() {
        return List.copyOf(tests);
    }
}
