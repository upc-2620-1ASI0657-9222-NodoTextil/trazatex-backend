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

/**
 * El análisis de impacto de una falla confirmada (US33, docs/domain/traceability.md): la <b>familia</b> del lote fallado son el propio lote fallado, sus antecesores
 * (hasta {@value #MAX_LEVELS} niveles hacia atrás, históricos incluidos: solo sirven para reconstruir) y todo lo que desciende de cualquiera de ellos (hasta
 * {@value #MAX_LEVELS} niveles hacia adelante <b>desde cada punto de partida</b>). Es un dato interno: ve lotes de todas las empresas, no pasa por la política de
 * privacidad B2B y su resultado solo sale por el evento.
 *
 * <p><b>Costo</b>: el recorrido hacia atrás es el de las consultas ({@link LineageTraversal}) y el de hacia adelante es el mismo recorrido por niveles pero con todos los
 * puntos de partida a la vez, así que cuesta como máximo 2 consultas por nivel en total (no una pasada por antecesor) y nunca enumera caminos. Para decidir el
 * {@code commonAncestorBatchId} de cada nodo (ver {@link ImpactRule}) cada nodo guarda el mejor punto de partida que lo alcanza dentro del límite; un punto de
 * partida peor no puede mejorar nada de lo que ya alcanza uno mejor desde antes, así que se descarta (un nodo solo se vuelve a expandir si llega uno estrictamente mejor).
 *
 * <p>Si la familia contiene un nodo provisional o incompleto, o el lote fallado aún no está completo, lanza {@link ImpactAnalysisIncompleteException} (reintentable): no
 * se devuelve un resultado parcial. Si la familia sigue más allá del límite de niveles, devuelve lo que entra en él con {@code truncated}.
 */
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
            // El lote fallado todavía no llegó (o está a medias): sus eventos de Production pueden venir después de FailureConfirmed
            throw new ImpactAnalysisIncompleteException(sourceBatchId, Set.of(sourceBatchId));
        }

        // 1. Los puntos de partida: el lote fallado (nivel 0) y sus antecesores, cada uno con su nivel hacia atrás
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

        // 2. Hacia adelante desde todos a la vez, nivel por nivel; cada nodo guarda el mejor punto de partida que lo alcanza
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
            // Pasado el límite: ¿quedaba algo que un nivel más habría alcanzado?
            for (Link link : port.childrenOf(frontier.keySet())) {
                if (LineageTraversal.usable(link) && improves(frontier.get(link.frontierBatchId()), bestStart.get(link.neighbor().batchId()), backwardLevel)) {
                    truncated = true;
                }
            }
        }
        if (!incomplete.isEmpty()) {
            throw new ImpactAnalysisIncompleteException(sourceBatchId, incomplete);
        }

        // 3. Qué se marca, con su antecesor común, en un orden estable
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

    /** ¿Mejora {@code candidate} lo que ya se sabe de un nodo? Un nodo nuevo siempre; uno conocido solo con un punto de partida estrictamente más cercano al lote fallado. */
    private static boolean improves(UUID candidate, UUID current, Map<UUID, Integer> backwardLevel) {
        return current == null || ImpactRule.isCloserToTheFailedBatch(backwardLevel.get(candidate), candidate, backwardLevel.get(current), current);
    }
}
