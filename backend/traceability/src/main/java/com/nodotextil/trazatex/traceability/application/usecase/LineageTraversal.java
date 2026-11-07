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

/**
 * Recorre la genealogía <b>nivel por nivel</b> desde un lote, en una dirección. Un conjunto de nodos ya visitados hace que cada nodo se
 * resuelva una vez: un grafo con mezclas y divisiones (en forma de diamante) tiene exponencialmente más caminos que nodos, y aquí cuesta
 * como máximo 2 consultas por nivel (más una de comprobación del corte), lineal en nodos y aristas. Nunca enumera caminos.
 *
 * <p>Reglas (docs/domain/traceability.md):
 * <ul>
 *   <li>Un nivel es un salto de lote a lote ({@code SPLIT_INTO} cuenta 1; {@code INPUT_OF → PRODUCED} también 1). El nivel de un nodo
 *       es su distancia más corta al lote consultado.</li>
 *   <li>Máximo {@value #MAX_LEVELS} niveles. Pasado el límite, {@code truncated} dice si quedaba algo (completo o no).</li>
 *   <li>El recorrido <b>se detiene</b> en un nodo incompleto: no lo devuelve, no lo cruza ni cuenta para el límite, y {@code complete}
 *       pasa a falso. Tampoco devuelve la arista que llega a él.</li>
 *   <li>Seguro ante ciclos (un grafo corrupto no lo cuelga): los visitados no se vuelven a recorrer.</li>
 * </ul>
 */
final class LineageTraversal {

    static final int MAX_LEVELS = com.nodotextil.trazatex.traceability.domain.model.graph.Lineage.MAX_LEVELS;

    /** Lo recorrido en una dirección, sin incluir el lote de partida. */
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
            // Se recorrieron los niveles permitidos y quedan nodos en la frontera: ¿hay algo más allá? Lo que enlaza con nodos ya
            // incluidos completa las aristas entre ellos; cualquier otro vecino (completo o no) significa que se cortó.
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

    /** Los nodos que hacen inutilizable el salto (el vecino y/o la transformación incompletos): lo que el análisis de impacto dice que "falta". */
    static void collectIncomplete(Link link, Set<UUID> into) {
        if (!link.neighbor().isComplete()) {
            into.add(link.neighbor().batchId());
        }
        if (link.via() != null && !link.via().isComplete()) {
            into.add(link.via().transformationId());
        }
    }

    /** Las aristas del salto y la transformación intermedia (si la hay), con la orientación real de la relación. */
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
