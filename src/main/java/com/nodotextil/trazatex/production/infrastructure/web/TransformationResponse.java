package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.domain.Transformation;
import com.nodotextil.trazatex.production.domain.TransformationType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TransformationResponse(
        UUID id,
        UUID companyId,
        UUID operatorId,
        UUID machineId,
        TransformationType type,
        List<UUID> inputBatchIds,
        List<UUID> outputBatchIds,
        LocalDateTime startedAt,
        LocalDateTime completedAt) {

    static TransformationResponse from(Transformation transformation) {
        return new TransformationResponse(
                transformation.id(),
                transformation.companyId(),
                transformation.operatorId(),
                transformation.machineId(),
                transformation.type(),
                transformation.inputBatchIds(),
                transformation.outputBatchIds(),
                transformation.startedAt(),
                transformation.completedAt());
    }
}
