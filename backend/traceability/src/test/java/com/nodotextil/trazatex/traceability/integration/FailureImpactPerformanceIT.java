package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.traceability.application.port.in.AnalyzeFailureImpactUseCase;
import com.nodotextil.trazatex.traceability.application.dto.AnalyzeFailureImpactCommand;
import com.nodotextil.trazatex.traceability.application.port.out.LineageReadPort;
import com.nodotextil.trazatex.traceability.application.usecase.ImpactAnalyzer;
import com.nodotextil.trazatex.traceability.domain.model.impact.AffectedBatch;
import com.nodotextil.trazatex.traceability.domain.model.impact.ImpactResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cuánto cuesta el análisis de impacto (US33) sobre el diamante de 500 nodos de RNF-04 (con Neo4j de la memoria del compose): tiempo y consultas a Neo4j de una
 * falla confirmada en lotes de distintas capas (de 0 a 10 niveles de antecesores y de descendientes), con fases y estados de calidad repartidos al azar (semilla fija) para que
 * lo marcado no sea todo ni nada. <b>Y que el análisis dé lo correcto</b>: cada resultado se compara con el de una implementación ingenua independiente (un recorrido por
 * punto de partida sobre las aristas leídas aparte con Cypher); sin esa comparación una prueba de tiempo pasaría con un análisis que no hiciera nada.
 */
@Import(QueryCounting.Config.class)
class FailureImpactPerformanceIT extends IntegrationTestBase {

    static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    static final int WARMUP = 5;
    static final int REPETITIONS = 20;
    /** Por dirección 2 consultas por nivel × (10 niveles + 1 sonda), más buscar el lote fallado. */
    static final long QUERY_CAP = 2L * 2 * 11 + 1;

    @Autowired
    LineageReadPort port;
    @Autowired
    AnalyzeFailureImpactUseCase service;

    private final Map<UUID, Set<UUID>> children = new HashMap<>();
    private final Map<UUID, Set<UUID>> parents = new HashMap<>();
    private final Map<UUID, String> phase = new HashMap<>();
    private final Map<UUID, String> quality = new HashMap<>();
    private final Map<UUID, UUID> company = new HashMap<>();

    @BeforeEach
    void loadTheDiamondWithAMixOfPhasesAndQualities() {
        DiamondGraph.load(neo4j, A, B);
        assertThat(DiamondGraph.nodeCount(neo4j)).isEqualTo(500L);
        Random random = new Random(33);
        List<String> ids = new ArrayList<>();
        List<String> phases = new ArrayList<>();
        List<String> qualities = new ArrayList<>();
        String[] historic = {"PROCESSED", "SPLIT", "DISCARDED"};
        String[] statuses = {"NOT_REVIEWED", "CONFORMING", "FAILED", "PENDING_REEVALUATION", "POTENTIAL_DERIVED_FAILURE"};
        for (int layer = 0; layer < DiamondGraph.LAYERS; layer++) {
            for (int position = 0; position < DiamondGraph.WIDTH; position++) {
                UUID id = DiamondGraph.batchId(layer, position);
                String p = random.nextInt(100) < 60 ? "AVAILABLE" : historic[random.nextInt(historic.length)];
                String q = statuses[random.nextInt(statuses.length)];
                ids.add(id.toString());
                phases.add(p);
                qualities.add(q);
                phase.put(id, p);
                quality.put(id, q);
                company.put(id, position % 2 == 0 ? A : B);
            }
        }
        neo4j.query("UNWIND range(0, size($ids) - 1) AS i MATCH (b:Batch {batchId: $ids[i]}) SET b.phase = $phases[i], b.qualityStatus = $qualities[i]")
                .bind(ids).to("ids").bind(phases).to("phases").bind(qualities).to("qualities").run();
        // las aristas, leídas aparte con Cypher: lo que usa el oráculo
        neo4j.query("""
                MATCH (a:Batch)-[:SPLIT_INTO]->(b:Batch) RETURN a.batchId AS from, b.batchId AS to
                UNION
                MATCH (a:Batch)-[:INPUT_OF]->(:Transformation)-[:PRODUCED]->(b:Batch) RETURN a.batchId AS from, b.batchId AS to""").fetch().all().forEach(row -> {
            UUID from = UUID.fromString((String) row.get("from"));
            UUID to = UUID.fromString((String) row.get("to"));
            children.computeIfAbsent(from, key -> new HashSet<>()).add(to);
            parents.computeIfAbsent(to, key -> new HashSet<>()).add(from);
        });
    }

    private static Map<UUID, Integer> distances(Map<UUID, Set<UUID>> adjacency, UUID from, int limit) {
        Map<UUID, Integer> distance = new LinkedHashMap<>();
        distance.put(from, 0);
        List<UUID> frontier = List.of(from);
        for (int level = 1; level <= limit && !frontier.isEmpty(); level++) {
            List<UUID> next = new ArrayList<>();
            for (UUID node : frontier) {
                for (UUID neighbor : adjacency.getOrDefault(node, Set.of())) {
                    if (distance.putIfAbsent(neighbor, level) == null) {
                        next.add(neighbor);
                    }
                }
            }
            frontier = next;
        }
        return distance;
    }

    /** El oráculo: lo que hay que marcar y su antecesor común, con un recorrido por punto de partida y sin atajos. {@code startsOut[0]} = cuántos puntos de partida hubo. */
    private Map<UUID, UUID> oracle(UUID failed, int[] startsOut) {
        Map<UUID, Integer> backward = distances(parents, failed, 10);
        startsOut[0] = backward.size();
        Map<UUID, UUID> best = new HashMap<>();
        for (UUID start : backward.keySet()) {
            for (UUID node : distances(children, start, 10).keySet()) {
                UUID current = best.get(node);
                if (current == null || backward.get(start) < backward.get(current)
                        || backward.get(start).equals(backward.get(current)) && start.toString().compareTo(current.toString()) < 0) {
                    best.put(node, start);
                }
            }
        }
        Map<UUID, UUID> expected = new TreeMap<>();
        best.forEach((node, start) -> {
            if (!node.equals(failed) && "AVAILABLE".equals(phase.get(node)) && !"FAILED".equals(quality.get(node))) {
                expected.put(node, start);
            }
        });
        return expected;
    }

    private static double percentile(List<Long> sorted, double p) {
        return sorted.get(Math.max(0, Math.min((int) Math.ceil(p / 100.0 * sorted.size()) - 1, sorted.size() - 1))) / 1_000_000.0;
    }

    @Test
    void theAnalysisOfAFailureOnTheFiveHundredNodeDiamondIsCorrectAndCheap() {
        ImpactAnalyzer analyzer = new ImpactAnalyzer(port);
        List<Long> all = new ArrayList<>();
        long maxQueries = 0;
        int totalAffected = 0;
        int largest = 0;
        StringBuilder detail = new StringBuilder();
        for (int layer : new int[]{0, 2, 5, 8, 10}) {
            for (int position : new int[]{3, 14, 25}) {
                UUID failed = DiamondGraph.batchId(layer, position);
                int[] starts = new int[1];
                Map<UUID, UUID> expected = oracle(failed, starts);
                for (int i = 0; i < WARMUP; i++) {
                    analyzer.analyze(failed);
                }
                List<Long> times = new ArrayList<>();
                ImpactResult result = null;
                for (int i = 0; i < REPETITIONS; i++) {
                    long start = System.nanoTime();
                    result = analyzer.analyze(failed);
                    times.add(System.nanoTime() - start);
                }
                Collections.sort(times);
                all.addAll(times);
                QueryCounting.reset();
                analyzer.analyze(failed);
                long queries = QueryCounting.count();
                maxQueries = Math.max(maxQueries, queries);

                Map<UUID, UUID> actual = new TreeMap<>();
                for (AffectedBatch affected : result.affected()) {
                    actual.put(affected.batchId(), affected.commonAncestorBatchId());
                    assertThat(affected.companyId()).as("la empresa de cada afectado").isEqualTo(company.get(affected.batchId()));
                }
                assertThat(actual).as("lote fallado capa %d posición %d: el análisis es el de la implementación ingenua", layer, position).isEqualTo(expected);
                assertThat(result.truncated()).as("capa %d: 10 niveles de familia caben en el límite", layer).isFalse();
                assertThat(queries).as("consultas del análisis (capa %d)", layer).isLessThanOrEqualTo(QUERY_CAP);
                totalAffected += actual.size();
                largest = Math.max(largest, actual.size());
                detail.append(String.format("%n  capa %2d pos %2d: %3d puntos de partida, %3d afectados, %2d consultas, mediana %5.1f ms, máximo %5.1f ms", layer, position, starts[0], actual.size(), queries,
                        percentile(times, 50), times.getLast() / 1_000_000.0));
            }
        }
        Collections.sort(all);
        System.out.printf("ANÁLISIS DE IMPACTO en el diamante de 500 nodos (%d lotes fallados distintos × %d repeticiones tras %d de calentamiento): mediana=%.1f ms p95=%.1f ms "
                        + "máximo=%.1f ms; consultas por análisis ≤ %d (tope %d); afectados por falla: hasta %d (%d en total)%s%n",
                15, REPETITIONS, WARMUP, percentile(all, 50), percentile(all, 95), all.getLast() / 1_000_000.0, maxQueries, QUERY_CAP, largest, totalAffected, detail);

        assertThat(totalAffected).as("la comparación no es vacía: hay muchos afectados").isGreaterThan(300);
        assertThat(percentile(all, 95)).as("p95 en ms (referencia: el límite de RNF-04 para una consulta, 5 s)").isLessThanOrEqualTo(5_000.0);
    }

    @Test
    void theWholeEventWithItsOutboxRowOnTheDiamondFitsInOneTransactionAndCostsTheSame() {
        UUID failed = DiamondGraph.batchId(5, 14);
        int[] starts = new int[1];
        int expected = oracle(failed, starts).size();
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            long begin = System.nanoTime();
            service.apply(new AnalyzeFailureImpactCommand(UUID.randomUUID(), UUID.randomUUID(), failed));
            times.add(System.nanoTime() - begin);
        }
        Collections.sort(times);

        System.out.printf("EVENTO COMPLETO (análisis + fila del outbox en una transacción) para la falla de la capa 5 (%d puntos de partida, %d afectados): mediana=%.1f ms máximo=%.1f ms%n",
                starts[0], expected, percentile(times, 50), times.getLast() / 1_000_000.0);
        long rows = (Long) neo4j.query("MATCH (o:OutboxEvent) RETURN count(o) AS n").fetch().one().orElseThrow().get("n");
        assertThat(rows).as("una fila por falla").isEqualTo(15L);
        assertThat(percentile(times, 50)).isLessThanOrEqualTo(5_000.0);
    }
}
