package com.nodotextil.trazatex.traceability.application.usecase;

import com.nodotextil.trazatex.traceability.application.port.out.LineageReadPort;
import com.nodotextil.trazatex.traceability.application.port.out.LineageReadPort.Link;
import com.nodotextil.trazatex.traceability.domain.model.EdgeType;
import com.nodotextil.trazatex.traceability.domain.model.graph.BatchData;
import com.nodotextil.trazatex.traceability.domain.model.graph.GraphEdge;
import com.nodotextil.trazatex.traceability.domain.model.graph.Lineage.LeveledBatch;
import com.nodotextil.trazatex.traceability.domain.model.graph.Lineage.LeveledTransformation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class LineageTraversal {

    static final int MAX_LEVELS = com.nodotextil.trazatex.traceability.domain.model.graph.Lineage.MAX_LEVELS;

    record Walk(List<LeveledBatch> batches, List<LeveledTransformation> transformations, Set<GraphEdge> edges, boolean complete, boolean truncated,
                Set<UUID> incompleteNodes) {
    }

    private final LineageReadPort port;

    LineageTraversal(LineageReadPort port) {
        this.port = port;
    }

    Walk walk(BatchData start, boolean ancestors) {
        int sign = ancestors ? -1 : 1;
        Map<UUID, LeveledBatch> batches = new LinkedHashMap<>();
        Map<UUID, LeveledTransformation> transformations = new LinkedHashMap<>();
        Set<GraphEdge> edges = new LinkedHashSet<>();
        Set<UUID> seen = new HashSet<>();
        seen.add(start.batchId());
        boolean complete = true;
        boolean truncated = false;
        Set<UUID> incompleteNodes = new java.util.TreeSet<>();
        List<UUID> frontier = List.of(start.batchId());
        for (int level = 1; level <= MAX_LEVELS && !frontier.isEmpty(); level++) {
            List<UUID> next = new ArrayList<>();
            for (Link link : links(frontier, ancestors)) {
                if (!usable(link)) {
                    complete = false;
                    collectIncomplete(link, incompleteNodes);
                    continue;
                }
                record(link, ancestors, sign * level, transformations, edges);
                if (seen.add(link.neighbor().batchId())) {
                    batches.put(link.neighbor().batchId(), new LeveledBatch(link.neighbor(), sign * level));
                    next.add(link.neighbor().batchId());
                }
            }
            frontier = next;
        }
        if (!frontier.isEmpty()) {
            for (Link link : links(frontier, ancestors)) {
                if (usable(link) && seen.contains(link.neighbor().batchId())) {
                    record(link, ancestors, sign * MAX_LEVELS, transformations, edges);
                } else {
                    truncated = true;
                }
            }
        }
        return new Walk(List.copyOf(batches.values()), List.copyOf(transformations.values()), edges, complete, truncated, incompleteNodes);
    }

    private List<Link> links(List<UUID> frontier, boolean ancestors) {
        return ancestors ? port.parentsOf(frontier) : port.childrenOf(frontier);
    }

    static boolean usable(Link link) {
        return link.neighbor().isComplete() && (link.via() == null || link.via().isComplete());
    }

    static void collectIncomplete(Link link, Set<UUID> into) {
        if (!link.neighbor().isComplete()) {
            into.add(link.neighbor().batchId());
        }
        if (link.via() != null && !link.via().isComplete()) {
            into.add(link.via().transformationId());
        }
    }

    private static void record(Link link, boolean ancestors, int level, Map<UUID, LeveledTransformation> transformations, Set<GraphEdge> edges) {
        UUID frontier = link.frontierBatchId();
        UUID neighbor = link.neighbor().batchId();
        UUID source = ancestors ? neighbor : frontier;
        UUID target = ancestors ? frontier : neighbor;
        if (link.via() == null) {
            edges.add(new GraphEdge(EdgeType.SPLIT_INTO, source, target));
            return;
        }
        UUID transformation = link.via().transformationId();
        transformations.putIfAbsent(transformation, new LeveledTransformation(link.via(), level));
        edges.add(new GraphEdge(EdgeType.INPUT_OF, source, transformation));
        edges.add(new GraphEdge(EdgeType.PRODUCED, transformation, target));
    }
}
