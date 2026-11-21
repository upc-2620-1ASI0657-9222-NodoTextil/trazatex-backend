package com.nodotextil.trazatex.traceability.domain.model.graph;

import com.nodotextil.trazatex.traceability.domain.model.CompanyPrivacy;
import com.nodotextil.trazatex.traceability.domain.model.LineageDirection;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record Lineage(UUID startBatchId, LineageDirection direction, List<LeveledBatch> batches, List<LeveledTransformation> transformations,
                      List<GraphEdge> edges, Map<UUID, List<TransferRecord>> transfers, Map<UUID, String> companyNames,
                      Map<UUID, CompanyPrivacy> companyPrivacy, boolean complete, boolean truncated) {

    public static final int MAX_LEVELS = 10;

    public record LeveledBatch(BatchData data, int level) {
    }

    public record LeveledTransformation(TransformationData data, int level) {
    }
}
