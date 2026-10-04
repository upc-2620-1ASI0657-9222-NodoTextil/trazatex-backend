package com.nodotextil.trazatex.traceability.support;

import com.nodotextil.trazatex.traceability.domain.LineageEdge;
import com.nodotextil.trazatex.traceability.domain.LineageGraph;
import com.nodotextil.trazatex.traceability.domain.LineageRepository;

import java.util.*;

public class InMemoryLineageRepository implements LineageRepository {

    private final Map<String, LineageEdge> edges = new LinkedHashMap<>();
    private final Map<UUID, List<LineageEdge>> byChild = new HashMap<>();
    private final Map<UUID, List<LineageEdge>> byParent = new HashMap<>();

    @Override
    public synchronized void saveEdge(LineageEdge edge) {
        String key = edge.parentBatchId() + "->" + edge.childBatchId() + ":" + edge.type();
        if (edges.putIfAbsent(key, edge) == null) {
            byChild.computeIfAbsent(edge.childBatchId(), id -> new ArrayList<>()).add(edge);
            byParent.computeIfAbsent(edge.parentBatchId(), id -> new ArrayList<>()).add(edge);
        }
    }

    @Override
    public LineageGraph findAncestors(UUID batchId) {
        Set<LineageEdge> result = new LinkedHashSet<>();
        collectAncestors(batchId, result);
        return new LineageGraph(batchId, result);
    }

    @Override
    public LineageGraph findDescendants(UUID batchId) {
        Set<LineageEdge> result = new LinkedHashSet<>();
        collectDescendants(Set.of(batchId), result);
        return new LineageGraph(batchId, result);
    }

    @Override
    public LineageGraph findFamily(UUID batchId) {
        Set<LineageEdge> ancestorEdges = new LinkedHashSet<>();
        collectAncestors(batchId, ancestorEdges);
        Set<UUID> origins = new HashSet<>();
        origins.add(batchId);
        ancestorEdges.forEach(edge -> origins.add(edge.parentBatchId()));

        Set<LineageEdge> result = new LinkedHashSet<>();
        collectDescendants(origins, result);
        return new LineageGraph(batchId, result);
    }

    public synchronized Set<LineageEdge> edges() {
        return new LinkedHashSet<>(edges.values());
    }

    public synchronized void clear() {
        edges.clear();
        byChild.clear();
        byParent.clear();
    }

    private void collectAncestors(UUID batchId, Set<LineageEdge> result) {
        Set<UUID> visited = new HashSet<>(Set.of(batchId));
        Deque<UUID> queue = new ArrayDeque<>(List.of(batchId));
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            for (LineageEdge edge : byChild.getOrDefault(current, List.of())) {
                result.add(edge);
                if (visited.add(edge.parentBatchId())) {
                    queue.add(edge.parentBatchId());
                }
            }
        }
    }

    private void collectDescendants(Set<UUID> origins, Set<LineageEdge> result) {
        Set<UUID> visited = new HashSet<>(origins);
        Deque<UUID> queue = new ArrayDeque<>(origins);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            for (LineageEdge edge : byParent.getOrDefault(current, List.of())) {
                result.add(edge);
                if (visited.add(edge.childBatchId())) {
                    queue.add(edge.childBatchId());
                }
            }
        }
    }
}
