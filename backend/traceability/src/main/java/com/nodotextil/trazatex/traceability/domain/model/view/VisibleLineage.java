package com.nodotextil.trazatex.traceability.domain.model.view;

import com.nodotextil.trazatex.traceability.domain.model.LineageDirection;

import java.util.List;
import java.util.UUID;

public record VisibleLineage(UUID batchId, LineageDirection direction, int maxLevels, boolean complete, boolean truncated,
                             List<VisibleNode> nodes, List<VisibleEdge> edges) implements VisibleView {
}
