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

    public void completeDetails(
            String cause,
            BigDecimal affectedQuantityKg,
            String observations) {

        ensureStatus(FailureStatus.PENDING_DECISION);

        if (cause == null || cause.isBlank()) {
            throw new InvalidQualityControlException("Failure cause is required");
        }

        if (affectedQuantityKg == null || affectedQuantityKg.signum() <= 0) {
            throw new InvalidQualityControlException(
                    "Affected quantity must be greater than zero"
            );
        }

        if (observations == null || observations.isBlank()) {
            throw new InvalidQualityControlException(
                    "Failure observations are required"
            );
        }

        this.cause = cause;
        this.affectedQuantityKg = affectedQuantityKg;
        this.observations = observations;
    }

    public void sendToReevaluation() {
        ensureStatus(FailureStatus.PENDING_DECISION);
        ensureDetailsCompleted();
        this.status = FailureStatus.IN_REEVALUATION;
    }

    public void confirm() {
        ensureStatus(FailureStatus.PENDING_DECISION);
        ensureDetailsCompleted();
        this.status = FailureStatus.CONFIRMED;
    }

    public void resolve() {
        ensureStatus(FailureStatus.IN_REEVALUATION);
        this.status = FailureStatus.RESOLVED;
    }

    public void returnToPendingDecision() {
        ensureStatus(FailureStatus.IN_REEVALUATION);
        this.status = FailureStatus.PENDING_DECISION;
    }

    private void ensureDetailsCompleted() {
        if (cause == null || affectedQuantityKg == null || observations == null) {
            throw new InvalidQualityControlException(
                    "Failure details must be completed before making a decision"
            );
        }
    }

    private void ensureStatus(FailureStatus expected) {
        if (status != expected) {
            throw new InvalidQualityControlException(
                    "Failure action is not allowed for status " + status
            );
        }
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
