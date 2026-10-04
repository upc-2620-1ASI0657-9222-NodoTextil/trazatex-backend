package com.nodotextil.trazatex.production.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Transformation {

    private final UUID id;
    private final UUID companyId;
    private final UUID operatorId;
    private final UUID machineId;
    private final TransformationType type;
    private final List<UUID> inputBatchIds;
    private List<UUID> outputBatchIds;
    private final LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private final BigDecimal totalInputKg;
    private BigDecimal totalOutputKg;
    private BigDecimal wasteKg;
    private String wasteReason;
    private BigDecimal shrinkageKg;

    private Transformation(
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
        this.id = Objects.requireNonNull(id, "Transformation id is required");
        this.companyId = Objects.requireNonNull(companyId, "Company id is required");
        this.operatorId = Objects.requireNonNull(operatorId, "Operator id is required");
        this.machineId = Objects.requireNonNull(machineId, "Machine id is required");
        this.type = Objects.requireNonNull(type, "Transformation type is required");
        if (inputBatchIds == null || inputBatchIds.isEmpty()) {
            throw new InvalidTransformationException(
                    "A transformation requires at least one input batch");
        }
        this.inputBatchIds = List.copyOf(inputBatchIds);
        this.outputBatchIds = outputBatchIds == null ? List.of() : List.copyOf(outputBatchIds);
        this.startedAt = Objects.requireNonNull(startedAt, "Start date is required");
        this.completedAt = completedAt;
        if (totalInputKg == null || totalInputKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransformationException("Total input must be greater than zero");
        }
        this.totalInputKg = totalInputKg;
        if (completedAt == null) {
            this.totalOutputKg = null;
            this.wasteKg = null;
            this.wasteReason = null;
            this.shrinkageKg = null;
        } else {
            BigDecimal calculatedShrinkage = calculateShrinkage(
                    totalInputKg, totalOutputKg, wasteKg, wasteReason);
            if (shrinkageKg == null || shrinkageKg.compareTo(calculatedShrinkage) != 0) {
                throw new InvalidTransformationException("Invalid transformation balance");
            }
            this.totalOutputKg = totalOutputKg;
            this.wasteKg = wasteKg;
            this.wasteReason = wasteReason;
            this.shrinkageKg = calculatedShrinkage;
        }
    }

    public static Transformation start(
            UUID id,
            UUID companyId,
            UUID operatorId,
            UUID machineId,
            TransformationType type,
            List<UUID> inputBatchIds,
            LocalDateTime startedAt,
            BigDecimal totalInputKg) {
        return new Transformation(
                id,
                companyId,
                operatorId,
                machineId,
                type,
                inputBatchIds,
                List.of(),
                startedAt,
                null,
                totalInputKg,
                null,
                null,
                null,
                null);
    }

    public static Transformation reconstitute(
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
        return new Transformation(
                id,
                companyId,
                operatorId,
                machineId,
                type,
                inputBatchIds,
                outputBatchIds,
                startedAt,
                completedAt,
                totalInputKg,
                totalOutputKg,
                wasteKg,
                wasteReason,
                shrinkageKg);
    }

    public void complete(
            List<UUID> outputBatchIds,
            LocalDateTime completedAt,
            BigDecimal totalOutputKg,
            BigDecimal wasteKg,
            String wasteReason) {
        if (isCompleted()) {
            throw new InvalidTransformationException("Transformation is already completed");
        }
        if (outputBatchIds == null || outputBatchIds.isEmpty()) {
            throw new InvalidTransformationException(
                    "A completed transformation requires at least one output batch");
        }
        BigDecimal calculatedShrinkage = calculateShrinkage(
                totalInputKg, totalOutputKg, wasteKg, wasteReason);
        this.outputBatchIds = List.copyOf(outputBatchIds);
        this.completedAt = Objects.requireNonNull(completedAt, "Completion date is required");
        this.totalOutputKg = totalOutputKg;
        this.wasteKg = wasteKg;
        this.wasteReason = wasteReason;
        this.shrinkageKg = calculatedShrinkage;
    }

    private static BigDecimal calculateShrinkage(
            BigDecimal totalInputKg,
            BigDecimal totalOutputKg,
            BigDecimal wasteKg,
            String wasteReason) {
        if (totalOutputKg == null || totalOutputKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransformationException("Total output must be greater than zero");
        }
        if (wasteKg == null || wasteKg.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidTransformationException("Waste cannot be negative");
        }
        if (wasteKg.compareTo(BigDecimal.ZERO) > 0
                && (wasteReason == null || wasteReason.isBlank())) {
            throw new InvalidTransformationException(
                    "Waste reason is required when waste is greater than zero");
        }

        BigDecimal shrinkageKg = totalInputKg.subtract(totalOutputKg).subtract(wasteKg);
        if (shrinkageKg.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidTransformationException(
                    "Output plus waste cannot exceed total input");
        }
        BigDecimal accountedInput = totalOutputKg.add(shrinkageKg).add(wasteKg);
        if (totalInputKg.compareTo(accountedInput) != 0) {
            throw new InvalidTransformationException("Invalid transformation balance");
        }
        return shrinkageKg;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public UUID id() {
        return id;
    }

    public UUID companyId() {
        return companyId;
    }

    public UUID operatorId() {
        return operatorId;
    }

    public UUID machineId() {
        return machineId;
    }

    public TransformationType type() {
        return type;
    }

    public List<UUID> inputBatchIds() {
        return inputBatchIds;
    }

    public List<UUID> outputBatchIds() {
        return outputBatchIds;
    }

    public LocalDateTime startedAt() {
        return startedAt;
    }

    public LocalDateTime completedAt() {
        return completedAt;
    }

    public BigDecimal totalInputKg() {
        return totalInputKg;
    }

    public BigDecimal totalOutputKg() {
        return totalOutputKg;
    }

    public BigDecimal wasteKg() {
        return wasteKg;
    }

    public String wasteReason() {
        return wasteReason;
    }

    public BigDecimal shrinkageKg() {
        return shrinkageKg;
    }
}
