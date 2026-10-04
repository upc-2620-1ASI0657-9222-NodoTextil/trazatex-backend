package com.nodotextil.trazatex.traceability.application;

import com.nodotextil.trazatex.traceability.domain.LineageEdge;
import com.nodotextil.trazatex.traceability.domain.LineageRepository;
import com.nodotextil.trazatex.traceability.domain.LineageType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class RecordLineageUseCase {

    private final LineageRepository lineageRepository;

    public RecordLineageUseCase(LineageRepository lineageRepository) {
        this.lineageRepository = lineageRepository;
    }

    public void recordDivision(UUID parentBatchId, List<UUID> childBatchIds, LocalDateTime occurredAt) {
        childBatchIds.forEach(childId -> lineageRepository.saveEdge(
                new LineageEdge(parentBatchId, childId, LineageType.DIVISION, occurredAt)));
    }

    public void recordTransformation(
            List<UUID> inputBatchIds, List<UUID> outputBatchIds, LocalDateTime occurredAt) {
        for (UUID inputId : inputBatchIds) {
            for (UUID outputId : outputBatchIds) {
                lineageRepository.saveEdge(
                        new LineageEdge(inputId, outputId, LineageType.TRANSFORMATION, occurredAt));
            }
        }
    }
}
