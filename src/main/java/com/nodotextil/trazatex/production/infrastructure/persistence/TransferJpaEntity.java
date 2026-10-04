package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.TransferStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(schema = "production", name = "production_transfers")
class TransferJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_company_id", nullable = false, updatable = false)
    private UUID sourceCompanyId;

    @Column(name = "destination_company_id", nullable = false, updatable = false)
    private UUID destinationCompanyId;

    @ElementCollection
    @CollectionTable(
            schema = "production",
            name = "production_transfer_batches",
            joinColumns = @JoinColumn(name = "transfer_id", nullable = false))
    @Column(name = "batch_id", nullable = false)
    @OrderColumn(name = "batch_order")
    private List<UUID> batchIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransferStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    protected TransferJpaEntity() {
    }

    TransferJpaEntity(
            UUID id,
            UUID sourceCompanyId,
            UUID destinationCompanyId,
            List<UUID> batchIds,
            TransferStatus status,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            String rejectionReason) {
        this.id = id;
        this.sourceCompanyId = sourceCompanyId;
        this.destinationCompanyId = destinationCompanyId;
        this.batchIds = new ArrayList<>(batchIds);
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.rejectionReason = rejectionReason;
    }

    UUID getId() {
        return id;
    }

    UUID getSourceCompanyId() {
        return sourceCompanyId;
    }

    UUID getDestinationCompanyId() {
        return destinationCompanyId;
    }

    List<UUID> getBatchIds() {
        return batchIds;
    }

    TransferStatus getStatus() {
        return status;
    }

    LocalDateTime getStartedAt() {
        return startedAt;
    }

    LocalDateTime getCompletedAt() {
        return completedAt;
    }

    String getRejectionReason() {
        return rejectionReason;
    }
}
