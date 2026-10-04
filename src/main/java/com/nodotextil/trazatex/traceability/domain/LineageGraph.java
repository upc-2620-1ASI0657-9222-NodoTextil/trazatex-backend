package com.nodotextil.trazatex.traceability.domain;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record LineageGraph(UUID rootBatchId, Set<LineageEdge> edges) {

    public LineageGraph {
        Objects.requireNonNull(rootBatchId, "Root batch id is required");
        edges = Set.copyOf(edges);
    }

    public static LineageGraph empty(UUID rootBatchId) {
        return new LineageGraph(rootBatchId, Set.of());
    }

    public Set<UUID> batchIds() {
        Set<UUID> ids = new HashSet<>();
        ids.add(rootBatchId);
        edges.forEach(edge -> {
            ids.add(edge.parentBatchId());
            ids.add(edge.childBatchId());
        });
        return ids;
    }

    public LineageGraph mergedWith(LineageGraph other) {
        Set<LineageEdge> merged = new HashSet<>(edges);
        merged.addAll(other.edges);
        return new LineageGraph(rootBatchId, merged);
    }
}
