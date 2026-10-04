package com.nodotextil.trazatex.traceability.application;

import com.nodotextil.trazatex.traceability.application.port.BatchInfoPort;
import com.nodotextil.trazatex.traceability.domain.LineageGraph;
import com.nodotextil.trazatex.traceability.domain.LineageRepository;

import java.util.UUID;

public class GetLineageUseCase {

    private final LineageRepository lineageRepository;
    private final BatchInfoPort batchInfo;

    public GetLineageUseCase(LineageRepository lineageRepository, BatchInfoPort batchInfo) {
        this.lineageRepository = lineageRepository;
        this.batchInfo = batchInfo;
    }

    public LineageGraph execute(UUID batchId, LineageDirection direction) {
        batchInfo.findById(batchId).orElseThrow(() -> new LotNotFoundException(batchId));

        return switch (direction) {
            case BACKWARD -> lineageRepository.findAncestors(batchId);
            case FORWARD -> lineageRepository.findDescendants(batchId);
            case FULL -> lineageRepository.findAncestors(batchId)
                    .mergedWith(lineageRepository.findDescendants(batchId));
        };
    }
}
