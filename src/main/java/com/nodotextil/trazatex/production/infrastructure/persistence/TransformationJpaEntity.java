package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.TransformationType;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "production_transformations")
class TransformationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "operator_id", nullable = false, updatable = false)
    private UUID operatorId;

    @Column(name = "machine_id", nullable = false, updatable = false)
    private UUID machineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private TransformationType type;

    @ElementCollection
    @CollectionTable(
            name = "production_transformation_inputs",
            joinColumns = @JoinColumn(name = "transformation_id", nullable = false))
    @Column(name = "batch_id", nullable = false)
    @OrderColumn(name = "input_order")
    private List<UUID> inputBatchIds = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "production_transformation_outputs",
            joinColumns = @JoinColumn(name = "transformation_id", nullable = false))
    @Column(name = "batch_id", nullable = false)
    @OrderColumn(name = "output_order")
    private List<UUID> outputBatchIds = new ArrayList<>();

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "total_input_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalInputKg;

    @Column(name = "total_output_kg", precision = 19, scale = 4)
    private BigDecimal totalOutputKg;

    @Column(name = "waste_kg", precision = 19, scale = 4)
    private BigDecimal wasteKg;

    @Column(name = "waste_reason", length = 1000)
    private String wasteReason;

    @Column(name = "shrinkage_kg", precision = 19, scale = 4)
    private BigDecimal shrinkageKg;

    protected TransformationJpaEntity() {
    }

    TransformationJpaEntity(
            UUID id,
            UUID companyId,
            UUID operatorId,
            UUID machineId,
            TransformationType type,
            List<UUID> inputBatchIds,
            List<UUID> outputBatchIds,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            BigDecimal totalInputKg,
            BigDecimal totalOutputKg,
            BigDecimal wasteKg,
            String wasteReason,
            BigDecimal shrinkageKg) {
        this.id = id;
        this.companyId = companyId;
        this.operatorId = operatorId;
        this.machineId = machineId;
        this.type = type;
        this.inputBatchIds = new ArrayList<>(inputBatchIds);
        this.outputBatchIds = new ArrayList<>(outputBatchIds);
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.totalInputKg = totalInputKg;
        this.totalOutputKg = totalOutputKg;
        this.wasteKg = wasteKg;
        this.wasteReason = wasteReason;
        this.shrinkageKg = shrinkageKg;
    }

    UUID getId() {
        return id;
    }

    UUID getCompanyId() {
        return companyId;
    }

    UUID getOperatorId() {
        return operatorId;
    }

    UUID getMachineId() {
        return machineId;
    }

    TransformationType getType() {
        return type;
    }

    List<UUID> getInputBatchIds() {
        return inputBatchIds;
    }

    List<UUID> getOutputBatchIds() {
        return outputBatchIds;
    }

    LocalDateTime getStartedAt() {
        return startedAt;
    }

    LocalDateTime getCompletedAt() {
        return completedAt;
    }

    BigDecimal getTotalInputKg() {
        return totalInputKg;
    }

    BigDecimal getTotalOutputKg() {
        return totalOutputKg;
    }

    BigDecimal getWasteKg() {
        return wasteKg;
    }

    String getWasteReason() {
        return wasteReason;
    }

    BigDecimal getShrinkageKg() {
        return shrinkageKg;
    }
}
