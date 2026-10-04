package com.nodotextil.trazatex.production.domain;

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

    private Transformation(
            UUID id,
            UUID companyId,
            UUID operatorId,
            UUID machineId,
            TransformationType type,
            List<UUID> inputBatchIds,
            List<UUID> outputBatchIds,
            LocalDateTime startedAt,
            LocalDateTime completedAt) {
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
    }

    public static Transformation start(
            UUID id,
            UUID companyId,
            UUID operatorId,
            UUID machineId,
            TransformationType type,
            List<UUID> inputBatchIds,
            LocalDateTime startedAt) {
        return new Transformation(
                id,
                companyId,
                operatorId,
                machineId,
                type,
                inputBatchIds,
                List.of(),
                startedAt,
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
            LocalDateTime completedAt) {
        return new Transformation(
                id,
                companyId,
                operatorId,
                machineId,
                type,
                inputBatchIds,
                outputBatchIds,
                startedAt,
                completedAt);
    }

    public void complete(List<UUID> outputBatchIds, LocalDateTime completedAt) {
        if (isCompleted()) {
            throw new InvalidTransformationException("Transformation is already completed");
        }
        if (outputBatchIds == null || outputBatchIds.isEmpty()) {
            throw new InvalidTransformationException(
                    "A completed transformation requires at least one output batch");
        }
        this.outputBatchIds = List.copyOf(outputBatchIds);
        this.completedAt = Objects.requireNonNull(completedAt, "Completion date is required");
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
}
