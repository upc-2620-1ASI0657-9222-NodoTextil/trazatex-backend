package com.nodotextil.trazatex.traceability.domain;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class FailureImpactAnalyzer {

    public List<AffectedLot> analyze(
            UUID failedBatchId, LineageGraph family, Predicate<UUID> isAvailable) {

        Map<UUID, List<LineageEdge>> edgesByChild = family.edges().stream()
                .collect(Collectors.groupingBy(LineageEdge::childBatchId));
        Map<UUID, List<LineageEdge>> edgesByParent = family.edges().stream()
                .collect(Collectors.groupingBy(LineageEdge::parentBatchId));

        Set<UUID> origins = ancestorsIncludingSelf(failedBatchId, edgesByChild);

        Map<UUID, AffectedLot.Reason> reached = new LinkedHashMap<>();
        Set<UUID> visited = new HashSet<>(origins);
        Deque<UUID> queue = new ArrayDeque<>(origins);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            for (LineageEdge edge : edgesByParent.getOrDefault(current, List.of())) {
                if (visited.add(edge.childBatchId())) {
                    reached.put(edge.childBatchId(), reasonOf(edge.type()));
                    queue.add(edge.childBatchId());
                }
            }
        }

        List<AffectedLot> affected = new ArrayList<>();
        reached.forEach((batchId, reason) -> {
            if (!batchId.equals(failedBatchId) && isAvailable.test(batchId)) {
                affected.add(new AffectedLot(batchId, reason));
            }
        });
        return List.copyOf(affected);
    }

    private static Set<UUID> ancestorsIncludingSelf(
            UUID batchId, Map<UUID, List<LineageEdge>> edgesByChild) {
        Set<UUID> result = new HashSet<>();
        result.add(batchId);
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(batchId);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            for (LineageEdge edge : edgesByChild.getOrDefault(current, List.of())) {
                if (result.add(edge.parentBatchId())) {
                    queue.add(edge.parentBatchId());
                }
            }
        }
        return result;
    }

    private static AffectedLot.Reason reasonOf(LineageType type) {
        return switch (type) {
            case DIVISION -> AffectedLot.Reason.DIVISION_BRANCH;
            case TRANSFORMATION -> AffectedLot.Reason.TRANSFORMATION_OUTPUT;
        };
    }
}
