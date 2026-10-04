package com.nodotextil.trazatex.production.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Transfer {

    private final UUID id;
    private final UUID sourceCompanyId;
    private final UUID destinationCompanyId;
    private final List<UUID> batchIds;
    private TransferStatus status;
    private final LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String rejectionReason;

    private Transfer(
            UUID id,
            UUID sourceCompanyId,
            UUID destinationCompanyId,
            List<UUID> batchIds,
            TransferStatus status,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            String rejectionReason) {
        this.id = Objects.requireNonNull(id, "Transfer id is required");
        this.sourceCompanyId = Objects.requireNonNull(
                sourceCompanyId, "Source company id is required");
        this.destinationCompanyId = Objects.requireNonNull(
                destinationCompanyId, "Destination company id is required");
        if (sourceCompanyId.equals(destinationCompanyId)) {
            throw new InvalidTransferException(
                    "Destination company must differ from source company");
        }
        if (batchIds == null || batchIds.isEmpty()) {
            throw new InvalidTransferException("A transfer requires at least one batch");
        }
        this.batchIds = List.copyOf(batchIds);
        this.status = Objects.requireNonNull(status, "Transfer status is required");
        this.startedAt = Objects.requireNonNull(startedAt, "Start date is required");
        this.completedAt = completedAt;
        this.rejectionReason = rejectionReason;
    }

    public static Transfer start(
            UUID id,
            UUID sourceCompanyId,
            UUID destinationCompanyId,
            List<UUID> batchIds,
            LocalDateTime startedAt) {
        return new Transfer(
                id,
                sourceCompanyId,
                destinationCompanyId,
                batchIds,
                TransferStatus.PENDING,
                startedAt,
                null,
                null);
    }

    public static Transfer reconstitute(
            UUID id,
            UUID sourceCompanyId,
            UUID destinationCompanyId,
            List<UUID> batchIds,
            TransferStatus status,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            String rejectionReason) {
        return new Transfer(
                id,
                sourceCompanyId,
                destinationCompanyId,
                batchIds,
                status,
                startedAt,
                completedAt,
                rejectionReason);
    }

    public void accept(LocalDateTime acceptedAt) {
        ensurePending();
        status = TransferStatus.RECEIVED;
        completedAt = Objects.requireNonNull(acceptedAt, "Acceptance date is required");
    }

    public void reject(String reason, LocalDateTime rejectedAt) {
        ensurePending();
        if (reason == null || reason.isBlank()) {
            throw new InvalidTransferException("Rejection reason is required");
        }
        status = TransferStatus.REJECTED;
        rejectionReason = reason;
        completedAt = Objects.requireNonNull(rejectedAt, "Rejection date is required");
    }

    private void ensurePending() {
        if (status != TransferStatus.PENDING) {
            throw new InvalidTransferException("Transfer is already resolved");
        }
    }

    public UUID id() {
        return id;
    }

    public UUID sourceCompanyId() {
        return sourceCompanyId;
    }

    public UUID destinationCompanyId() {
        return destinationCompanyId;
    }

    public List<UUID> batchIds() {
        return batchIds;
    }

    public TransferStatus status() {
        return status;
    }

    public LocalDateTime startedAt() {
        return startedAt;
    }

    public LocalDateTime completedAt() {
        return completedAt;
    }

    public String rejectionReason() {
        return rejectionReason;
    }
}
