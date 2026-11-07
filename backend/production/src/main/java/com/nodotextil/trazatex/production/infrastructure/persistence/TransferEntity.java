package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.model.TransferStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "transfers")
class TransferEntity {

    @Id
    UUID id;
    @Column(name = "origin_company_id", nullable = false, updatable = false)
    UUID originCompanyId;
    @Column(name = "target_company_id", nullable = false, updatable = false)
    UUID targetCompanyId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    TransferStatus status;
    @Column(name = "departure_time", nullable = false, updatable = false)
    Instant departureTime;
    @Column(name = "resolved_at")
    Instant resolvedAt;
    @Column(name = "rejection_reason")
    String rejectionReason;
    @Column(name = "initiated_by", nullable = false, updatable = false)
    UUID initiatedBy;
    @Column(name = "resolved_by")
    UUID resolvedBy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "transfer_batches", joinColumns = @JoinColumn(name = "transfer_id"))
    @OrderColumn(name = "position")
    @Column(name = "batch_id", nullable = false)
    List<UUID> batchIds = new ArrayList<>();

    protected TransferEntity() {
    }
}
