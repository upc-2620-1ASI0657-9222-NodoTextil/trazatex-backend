package com.nodotextil.trazatex.traceability.application.usecase;

import com.nodotextil.trazatex.traceability.application.port.out.LineageReadPort;
import com.nodotextil.trazatex.traceability.application.port.out.LineageReadPort.Link;
import com.nodotextil.trazatex.traceability.domain.exception.ImpactAnalysisIncompleteException;
import com.nodotextil.trazatex.traceability.domain.model.graph.BatchData;
import com.nodotextil.trazatex.traceability.domain.model.graph.Lineage;
import com.nodotextil.trazatex.traceability.domain.model.impact.AffectedBatch;
import com.nodotextil.trazatex.traceability.domain.model.impact.ImpactResult;
import com.nodotextil.trazatex.traceability.domain.service.ImpactRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class ImpactAnalyzer {

    static final int MAX_LEVELS = Lineage.MAX_LEVELS;

    private final LineageReadPort port;
    private final LineageTraversal traversal;

    public ImpactAnalyzer(LineageReadPort port) {
        this.port = port;
        this.traversal = new LineageTraversal(port);
    }

    public ImpactResult analyze(UUID sourceBatchId) {
        BatchData source = port.findBatch(sourceBatchId).orElse(null);
        if (source == null || !source.isComplete()) {
            throw new ImpactAnalysisIncompleteException(sourceBatchId, Set.of(sourceBatchId));
        }

        LineageTraversal.Walk backwards = traversal.walk(source, true);
        Map<UUID, BatchData> data = new HashMap<>();
        Map<UUID, Integer> backwardLevel = new HashMap<>();
        data.put(source.batchId(), source);
        backwardLevel.put(source.batchId(), 0);
        backwards.batches().forEach(leveled -> {
            data.put(leveled.data().batchId(), leveled.data());
            backwardLevel.put(leveled.data().batchId(), -leveled.level());
        });
        Set<UUID> incomplete = new TreeSet<>(backwards.incompleteNodes());
        boolean truncated = backwards.truncated();

        Map<UUID, UUID> bestStart = new HashMap<>();
        Map<UUID, UUID> frontier = new LinkedHashMap<>();
        for (UUID start : backwardLevel.keySet()) {
            bestStart.put(start, start);
            frontier.put(start, start);
        }
        for (int level = 1; level <= MAX_LEVELS && !frontier.isEmpty(); level++) {
            Map<UUID, UUID> next = new LinkedHashMap<>();
            for (Link link : port.childrenOf(frontier.keySet())) {
                if (!LineageTraversal.usable(link)) {
                    LineageTraversal.collectIncomplete(link, incomplete);
                    continue;
                }
                UUID node = link.neighbor().batchId();
                UUID start = frontier.get(link.frontierBatchId());
                data.putIfAbsent(node, link.neighbor());
                if (improves(start, bestStart.get(node), backwardLevel)) {
                    bestStart.put(node, start);
                    next.put(node, start);
                }
            }
            frontier = next;
        }
        if (!frontier.isEmpty()) {
            for (Link link : port.childrenOf(frontier.keySet())) {
                if (LineageTraversal.usable(link) && improves(frontier.get(link.frontierBatchId()), bestStart.get(link.neighbor().batchId()), backwardLevel)) {
                    truncated = true;
                }
            }
        }
        if (!incomplete.isEmpty()) {
            throw new ImpactAnalysisIncompleteException(sourceBatchId, incomplete);
        }

        List<AffectedBatch> affected = new ArrayList<>();
        bestStart.forEach((node, start) -> {
            BatchData batch = data.get(node);
            if (ImpactRule.isAffected(batch, sourceBatchId)) {
                affected.add(new AffectedBatch(node, batch.companyId(), start));
            }
        });
        affected.sort(Comparator.comparing(batch -> batch.batchId().toString()));
        return new ImpactResult(sourceBatchId, affected, truncated);
    }

    private static boolean improves(UUID candidate, UUID current, Map<UUID, Integer> backwardLevel) {
        return current == null || ImpactRule.isCloserToTheFailedBatch(backwardLevel.get(candidate), candidate, backwardLevel.get(current), current);
    }
}
