package com.nodotextil.trazatex.quality.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Failure {

    private final UUID id;
    private final UUID qualityControlId;
    private final UUID batchId;
    private final List<UUID> failedTestIds;
    private FailureStatus status;
    private String cause;
    private BigDecimal affectedQuantityKg;
    private String observations;
    private final LocalDateTime createdAt;

    public Failure(
            UUID id,
            UUID qualityControlId,
            UUID batchId,
            List<UUID> failedTestIds,
            LocalDateTime createdAt) {

        this.id = Objects.requireNonNull(id, "Failure id is required");
        this.qualityControlId = Objects.requireNonNull(
                qualityControlId,
                "Quality control id is required"
        );
        this.batchId = Objects.requireNonNull(batchId, "Batch id is required");
        this.failedTestIds = List.copyOf(
                Objects.requireNonNull(failedTestIds, "Failed tests are required")
        );
        this.createdAt = Objects.requireNonNull(
                createdAt,
                "Failure creation date is required"
        );

        if (failedTestIds.isEmpty()) {
            throw new InvalidQualityControlException(
                    "A failure requires at least one failed test"
            );
        }

        this.status = FailureStatus.PENDING_DECISION;
    }

    public UUID getId() {
        return id;
    }

    public UUID getQualityControlId() {
        return qualityControlId;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public List<UUID> getFailedTestIds() {
        return failedTestIds;
    }

    public FailureStatus getStatus() {
        return status;
    }

    public String getCause() {
        return cause;
    }

    public BigDecimal getAffectedQuantityKg() {
        return affectedQuantityKg;
    }

    public String getObservations() {
        return observations;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
