package com.nodotextil.trazatex.traceability.application.usecase;

import com.nodotextil.trazatex.traceability.domain.exception.ImpactAnalysisIncompleteException;
import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;
import com.nodotextil.trazatex.traceability.domain.model.impact.AffectedBatch;
import com.nodotextil.trazatex.traceability.domain.model.impact.ImpactResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static com.nodotextil.trazatex.traceability.application.usecase.FakeLineageReadPort.COMPANY_A;
import static com.nodotextil.trazatex.traceability.application.usecase.FakeLineageReadPort.COMPANY_B;
import static com.nodotextil.trazatex.traceability.application.usecase.FakeLineageReadPort.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El análisis de impacto de una falla confirmada (US33): la familia del lote fallado, qué se marca y su {@code commonAncestorBatchId}, sin Neo4j. Incluye una prueba
 * diferencial contra una implementación ingenua (un recorrido por punto de partida) en grafos aleatorios con semilla.
 */
class ImpactAnalyzerTest {

    private final FakeLineageReadPort graph = new FakeLineageReadPort();
    private final ImpactAnalyzer analyzer = new ImpactAnalyzer(graph);

    private ImpactResult analyze(String failed) {
        return analyzer.analyze(id(failed));
    }

    private final Map<UUID, String> registered = new HashMap<>();

    /** Los afectados como {@code lote → antecesor común}, por nombre, para leerlos. */
    private Map<String, String> affected(ImpactResult result) {
        Map<String, String> out = new TreeMap<>();
        for (AffectedBatch batch : result.affected()) {
            out.put(registered.getOrDefault(batch.batchId(), batch.batchId().toString()), registered.getOrDefault(batch.commonAncestorBatchId(), batch.commonAncestorBatchId().toString()));
        }
        return out;
    }

    /** Registra los nombres de los lotes para poder leer los resultados. */
    private void register(String... names) {
        for (String name : names) {
            registered.put(id(name), name);
        }
    }

    // ---- el lote fallado sin antecesores ----

    @Test
    void aRegisteredBatchWithoutParentsThatWasSplitAndTransformedStillFindsItsDerivatives() {
        // F no tiene padres. Se dividió en F1 y F2; F1 se mezcló con X (de otro origen) y dio U
        graph.batch("F", "F1", "F2", "X", "U").split("F", "F1", "F2").transform("T", List.of("F1", "X"), List.of("U"));
        graph.phase("F", BatchPhase.SPLIT).phase("F1", BatchPhase.PROCESSED).phase("X", BatchPhase.PROCESSED);
        register("F", "F1", "F2", "X", "U");

        ImpactResult result = analyze("F");

        assertThat(affected(result)).as("F2 y U siguen disponibles y descienden de F; F1 y X están procesados; F es el fallado").containsExactly(Map.entry("F2", "F"), Map.entry("U", "F"));
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void theFailedBatchNeverAppearsEvenIfItIsStillAvailable() {
        graph.batch("F", "H1").split("F", "H1");
        register("F", "H1");

        assertThat(affected(analyze("F"))).containsOnlyKeys("H1");
    }

    // ---- hacia atrás y la misma familia ----

    @Test
    void aSiblingIsFoundThroughAHistoricAncestorAndTheAncestorItselfIsNotReturned() {
        // G (dividido, histórico) → S (el fallado, descartado) y T (disponible)
        graph.batch("G", "S", "T").split("G", "S", "T");
        graph.phase("G", BatchPhase.SPLIT).phase("S", BatchPhase.DISCARDED);
        register("G", "S", "T");

        assertThat(affected(analyze("S"))).containsExactly(Map.entry("T", "G"));
    }

    @Test
    void theDescendantsOfTheRelatedBatchesThatContinueAvailableAreReviewedAndTheDividedOneIsOnlyUsedToRebuild() {
        graph.batch("G", "S", "T", "T1", "T2").split("G", "S", "T").split("T", "T1", "T2");
        graph.phase("G", BatchPhase.SPLIT).phase("S", BatchPhase.DISCARDED).phase("T", BatchPhase.SPLIT);
        register("G", "S", "T", "T1", "T2");

        assertThat(affected(analyze("S"))).containsExactly(Map.entry("T1", "G"), Map.entry("T2", "G"));
    }

    @Test
    void historicBatchesAreNeverMarkedWhateverTheirQuality() {
        graph.batch("G", "S", "P", "D", "Sp", "A").split("G", "S", "P", "D", "Sp", "A");
        graph.phase("G", BatchPhase.SPLIT).phase("S", BatchPhase.DISCARDED).phase("P", BatchPhase.PROCESSED).phase("D", BatchPhase.DISCARDED).phase("Sp", BatchPhase.SPLIT);
        register("G", "S", "P", "D", "Sp", "A");

        assertThat(affected(analyze("S"))).containsOnlyKeys("A");
    }

    @ParameterizedTest
    @EnumSource(BatchPhase.class)
    void onlyAvailableBatchesAreMarkedWhateverThePhase(BatchPhase phase) {
        graph.batch("S", "X").split("S", "X").phase("X", phase);
        register("S", "X");

        assertThat(affected(analyze("S")).containsKey("X")).isEqualTo(phase == BatchPhase.AVAILABLE);
    }

    // ---- qué estados de calidad cuentan (decisión del usuario: la regla literal) ----

    @ParameterizedTest
    @EnumSource(QualityStatus.class)
    void everyQualityStatusIsMarkedExceptFailed(QualityStatus status) {
        graph.batch("S", "X").split("S", "X").quality("X", status, false);
        register("S", "X");

        assertThat(affected(analyze("S")).containsKey("X")).as("calidad %s", status).isEqualTo(status != QualityStatus.FAILED);
    }

    @Test
    void aBatchAlreadyHeldOrAlreadyFlaggedAsPotentialDerivedFailureIsMarkedAgain() {
        graph.batch("S", "H", "P", "R").split("S", "H", "P", "R");
        graph.quality("H", QualityStatus.NOT_REVIEWED, true).quality("P", QualityStatus.POTENTIAL_DERIVED_FAILURE, true).quality("R", QualityStatus.PENDING_REEVALUATION, true);
        register("S", "H", "P", "R");

        assertThat(affected(analyze("S"))).as("la retención no excluye, y una segunda falla confirmada es información nueva").containsOnlyKeys("H", "P", "R");
    }

    // ---- commonAncestorBatchId ----

    @Test
    void theNearestAncestorToTheFailedBatchWinsAndTheFailedBatchItselfIsTheClosestOfAll() {
        // A2 → A1 → S; X desciende de A2 (por otra rama), de A1 y de S
        graph.batch("A2", "A1", "S", "X", "Y").split("A2", "A1").split("A1", "S");
        graph.transform("TX", List.of("A2", "A1", "S"), List.of("X"));
        graph.split("A2", "Y");
        graph.phase("A2", BatchPhase.PROCESSED).phase("A1", BatchPhase.SPLIT).phase("S", BatchPhase.DISCARDED);
        register("A2", "A1", "S", "X", "Y");

        assertThat(affected(analyze("S"))).as("X desciende de S (nivel 0): el más cercano; Y solo de A2").containsExactly(Map.entry("X", "S"), Map.entry("Y", "A2"));
    }

    @Test
    void whenTwoAncestorsAreEquallyCloseTheSmallerBatchIdWins() {
        // P y Q son las dos entradas (nivel 1) de la transformación que produjo S; X sale de mezclar P y Q
        graph.batch("P", "Q", "S", "X").transform("TS", List.of("P", "Q"), List.of("S")).transform("TX", List.of("P", "Q"), List.of("X"));
        graph.phase("P", BatchPhase.PROCESSED).phase("Q", BatchPhase.PROCESSED).phase("S", BatchPhase.DISCARDED);
        register("P", "Q", "S", "X");
        String smaller = id("P").toString().compareTo(id("Q").toString()) < 0 ? "P" : "Q";

        assertThat(affected(analyze("S"))).containsExactly(Map.entry("X", smaller));
    }

    @Test
    void theLevelIsTheDistanceBackwardsFromTheFailedBatchNotTheDistanceToTheAffected() {
        // A1 (nivel 1) llega a X por una cadena larga; A2 (nivel 2) llega directo: gana A1 aunque esté más lejos de X
        graph.batch("A2", "A1", "S", "X", "m1", "m2", "m3").split("A2", "A1").split("A1", "S").split("A1", "m1").split("m1", "m2").split("m2", "m3").split("m3", "X");
        graph.split("A2", "X");
        graph.phase("A2", BatchPhase.SPLIT).phase("A1", BatchPhase.SPLIT).phase("S", BatchPhase.DISCARDED).phase("m1", BatchPhase.SPLIT).phase("m2", BatchPhase.SPLIT)
                .phase("m3", BatchPhase.SPLIT);
        register("A2", "A1", "S", "X", "m1", "m2", "m3");

        assertThat(affected(analyze("S"))).containsExactly(Map.entry("X", "A1"));
    }

    @Test
    void anAncestorThatIsStillAvailableBelongsToTheFamilyAndIsItsOwnCommonAncestor() {
        // un dato atrasado deja a un antecesor como AVAILABLE
        graph.batch("A", "S").split("A", "S");
        graph.phase("S", BatchPhase.DISCARDED);
        register("A", "S");

        assertThat(affected(analyze("S"))).containsExactly(Map.entry("A", "A"));
    }

    @Test
    void theResultIsInTheOrderOfTheBatchIdAndRepeatable() {
        graph.batch("S", "a", "b", "c", "d", "e").split("S", "a", "b", "c", "d", "e");
        register("S", "a", "b", "c", "d", "e");

        List<UUID> first = analyze("S").affected().stream().map(AffectedBatch::batchId).toList();
        List<UUID> second = analyze("S").affected().stream().map(AffectedBatch::batchId).toList();

        assertThat(first).isEqualTo(second).isSortedAccordingTo(java.util.Comparator.comparing(UUID::toString));
        assertThat(first).hasSize(5);
    }

    // ---- sin filtro B2B ----

    @Test
    void batchesOfEveryCompanyAreSeenAndReportedWithTheirOwnCompany() {
        graph.batch("S", COMPANY_A).batch("B1", COMPANY_B).batch("A1", COMPANY_A).split("S", "B1", "A1");
        register("S", "B1", "A1");

        ImpactResult result = analyze("S");

        assertThat(result.affected()).extracting(AffectedBatch::companyId).containsExactlyInAnyOrder(COMPANY_A, COMPANY_B);
        assertThat(result.affected().stream().filter(a -> a.batchId().equals(id("B1"))).findFirst().orElseThrow().companyId()).isEqualTo(COMPANY_B);
    }

    // ---- límite de niveles ----

    @Test
    void tenLevelsForwardFromTheFailedBatchAreReviewedAndTheEleventhIsNotAndSaysSo() {
        graph.chain("n", 13); // n0 → … → n12; el fallado es n0
        for (int i = 0; i <= 12; i++) {
            register("n" + i);
        }

        ImpactResult result = analyze("n0");

        assertThat(affected(result).keySet()).containsExactlyInAnyOrder("n1", "n2", "n3", "n4", "n5", "n6", "n7", "n8", "n9", "n10");
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void exactlyTenLevelsAreNotTruncated() {
        graph.chain("n", 11);
        for (int i = 0; i <= 10; i++) {
            register("n" + i);
        }

        ImpactResult result = analyze("n0");

        assertThat(affected(result)).hasSize(10);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void tenLevelsBackwardAreStartingPointsAndTheEleventhIsNotButItsDescendantsOfTheLimitAreStillReachedFromTheNearerOnes() {
        // a0 → a1 → … → a12 = el fallado; cada ai (i < 12) también tiene una hoja li
        graph.chain("a", 13);
        for (int i = 0; i < 12; i++) {
            graph.batch("l" + i).split("a" + i, "l" + i);
            register("l" + i);
        }
        for (int i = 0; i <= 12; i++) {
            register("a" + i);
            if (i < 12) {
                graph.phase("a" + i, BatchPhase.SPLIT);
            }
        }
        graph.phase("a12", BatchPhase.DISCARDED);

        ImpactResult result = analyze("a12");

        // los antecesores a2…a11 (niveles 10…1) son puntos de partida: sus hojas salen. a1 y a0 (niveles 11 y 12) no
        assertThat(affected(result).keySet()).contains("l2", "l11").doesNotContain("l0", "l1");
        assertThat(result.truncated()).as("había antecesores más allá de 10 niveles").isTrue();
    }

    // ---- nodos incompletos ----

    @Test
    void aProvisionalBatchInTheFamilyForwardMakesTheAnalysisRetryableAndNamesIt() {
        graph.batch("S", "X").split("S", "X").provisional("LATE").transform("T", List.of("X"), List.of("LATE"));
        register("S", "X", "LATE");

        assertThatThrownBy(() -> analyze("S")).isInstanceOf(ImpactAnalysisIncompleteException.class)
                .satisfies(e -> assertThat(((ImpactAnalysisIncompleteException) e).missingNodes()).containsExactly(id("LATE")));
    }

    @Test
    void aProvisionalAncestorMakesItRetryable() {
        graph.batch("S").provisional("ANC").split("ANC", "S");
        register("S", "ANC");

        assertThatThrownBy(() -> analyze("S")).isInstanceOf(ImpactAnalysisIncompleteException.class)
                .satisfies(e -> assertThat(((ImpactAnalysisIncompleteException) e).missingNodes()).containsExactly(id("ANC")));
    }

    @Test
    void anIncompleteTransformationIsNamedToo() {
        graph.batch("S", "O").transform("T", COMPANY_A, List.of("S"), List.of("O"), true);

        assertThatThrownBy(() -> analyze("S")).isInstanceOf(ImpactAnalysisIncompleteException.class)
                .satisfies(e -> assertThat(((ImpactAnalysisIncompleteException) e).missingNodes()).contains(id("T")));
    }

    @Test
    void theFailedBatchItselfMissingOrIncompleteIsRetryableToo() {
        graph.provisional("S");

        assertThatThrownBy(() -> analyze("S")).isInstanceOf(ImpactAnalysisIncompleteException.class)
                .satisfies(e -> assertThat(((ImpactAnalysisIncompleteException) e).missingNodes()).containsExactly(id("S")));
        assertThatThrownBy(() -> analyzer.analyze(UUID.randomUUID())).isInstanceOf(ImpactAnalysisIncompleteException.class);
    }

    @Test
    void severalMissingNodesAreAllReportedAtOnce() {
        graph.batch("S", "X").split("S", "X").provisional("L1").provisional("L2").split("X", "L1", "L2");

        assertThatThrownBy(() -> analyze("S")).isInstanceOf(ImpactAnalysisIncompleteException.class)
                .satisfies(e -> assertThat(((ImpactAnalysisIncompleteException) e).missingNodes()).containsExactlyInAnyOrder(id("L1"), id("L2")));
    }

    @Test
    void anIncompleteNodeOutsideTheFamilyDoesNotBlockTheAnalysis() {
        // U se mezcla con Z, que nadie conoce (provisional): Z no desciende de nada de la familia ni es su antecesor
        graph.batch("S", "X", "U").split("S", "X").provisional("Z").transform("T", List.of("X", "Z"), List.of("U"));
        register("S", "X", "U");

        assertThat(affected(analyze("S"))).containsOnlyKeys("X", "U");
    }

    @Test
    void onceTheMissingNodeArrivesTheSameAnalysisSucceeds() {
        graph.batch("S", "X").split("S", "X").provisional("LATE").split("X", "LATE");
        register("S", "X", "LATE");
        assertThatThrownBy(() -> analyze("S")).isInstanceOf(ImpactAnalysisIncompleteException.class);

        graph.batch("LATE");

        assertThat(affected(analyze("S"))).containsOnlyKeys("X", "LATE");
    }

    @Test
    void theRetryableExceptionIsNeitherATrazatexExceptionNorAnIllegalArgumentSoItIsNotSentStraightToTheDeadLetterTopic() {
        assertThat(ImpactAnalysisIncompleteException.class.getSuperclass()).isEqualTo(RuntimeException.class);
    }

    // ---- sin familia ----

    @Test
    void aFailedBatchWithNoRelativesHasNoAffected() {
        graph.batch("S");

        assertThat(analyze("S").affected()).isEmpty();
    }

    @Test
    void ifEveryRelativeIsHistoricOrFailedTheListIsEmpty() {
        graph.batch("S", "P", "F").split("S", "P", "F").phase("P", BatchPhase.PROCESSED).quality("F", QualityStatus.FAILED, true);

        assertThat(analyze("S").affected()).isEmpty();
    }

    // ---- costo: no enumera caminos ni hace una pasada por antecesor ----

    @Test
    void theNumberOfLevelCallsDoesNotGrowWithTheNumberOfAncestorsOrNodes() {
        // 60 antecesores directos (entradas de una mezcla que produjo S), cada uno con 3 hijos: más de 240 nodos
        List<String> ancestors = new ArrayList<>();
        graph.batch("S");
        for (int i = 0; i < 60; i++) {
            graph.batch("p" + i, "p" + i + "a", "p" + i + "b", "p" + i + "c").split("p" + i, "p" + i + "a", "p" + i + "b", "p" + i + "c");
            ancestors.add("p" + i);
        }
        graph.transform("TS", ancestors, List.of("S"));

        ImpactResult result = analyze("S");

        assertThat(result.affected()).hasSize(60 + 180);
        // hacia atrás: 2 niveles (el de las entradas y el vacío); hacia adelante: 2 niveles. Nunca una llamada por antecesor
        assertThat(graph.levelCalls()).as("llamadas de nivel").isLessThanOrEqualTo(2 * (10 + 1));
    }

    @Test
    void aDiamondWithAThousandCrossingPathsIsSolvedLevelByLevel() {
        graph.batch("a0", "b0");
        for (int layer = 1; layer <= 10; layer++) {
            graph.batch("a" + layer, "b" + layer).split("a" + (layer - 1), "a" + layer, "b" + layer).split("b" + (layer - 1), "a" + layer, "b" + layer);
        }
        long start = System.nanoTime();
        ImpactResult result = analyze("a0");

        assertThat(result.affected()).as("a1…a10 y b1…b10: b0 no desciende de a0").hasSize(20);
        assertThat(graph.levelCalls()).isLessThanOrEqualTo(2 * (10 + 1));
        assertThat((System.nanoTime() - start) / 1_000_000).as("ms").isLessThan(2000);
    }

    @Test
    void aCorruptedCyclicGraphDoesNotHangTheAnalysis() {
        graph.batch("S", "A", "B").split("S", "A").split("A", "B").split("B", "A").split("B", "S");

        ImpactResult result = analyze("S");

        assertThat(result.affected()).extracting(AffectedBatch::batchId).containsExactlyInAnyOrder(id("A"), id("B"));
    }

    // ---- prueba diferencial contra una implementación ingenua ----

    /** Un grafo aleatorio por capas que se carga en el Fake y se resuelve aparte con el oráculo. */
    private static final class RandomGraph {
        final Map<String, Set<String>> children = new LinkedHashMap<>();
        final Map<String, BatchPhase> phase = new LinkedHashMap<>();
        final Map<String, QualityStatus> quality = new LinkedHashMap<>();
        final Map<String, UUID> company = new LinkedHashMap<>();
        final List<String> nodes = new ArrayList<>();
        final FakeLineageReadPort fake = new FakeLineageReadPort();

        RandomGraph(long seed, int layers, int width) {
            Random random = new Random(seed);
            List<List<String>> byLayer = new ArrayList<>();
            for (int layer = 0; layer < layers; layer++) {
                List<String> row = new ArrayList<>();
                int count = 1 + random.nextInt(width);
                for (int k = 0; k < count; k++) {
                    String name = "n" + layer + "_" + k;
                    row.add(name);
                    nodes.add(name);
                    children.put(name, new HashSet<>());
                    phase.put(name, random.nextInt(100) < 55 ? BatchPhase.AVAILABLE : BatchPhase.values()[random.nextInt(BatchPhase.values().length)]);
                    quality.put(name, QualityStatus.values()[random.nextInt(QualityStatus.values().length)]);
                    company.put(name, random.nextBoolean() ? COMPANY_A : COMPANY_B);
                    fake.batch(name, company.get(name));
                    fake.phase(name, phase.get(name));
                    fake.quality(name, quality.get(name), random.nextBoolean());
                }
                byLayer.add(row);
            }
            int transformations = 0;
            for (int layer = 1; layer < layers; layer++) {
                List<String> previous = byLayer.get(layer - 1);
                for (String name : byLayer.get(layer)) {
                    if (random.nextBoolean()) {
                        String parent = previous.get(random.nextInt(previous.size()));
                        fake.split(parent, name);
                        children.get(parent).add(name);
                    } else {
                        List<String> inputs = new ArrayList<>();
                        for (int i = 0, n = 1 + random.nextInt(3); i < n; i++) {
                            String input = previous.get(random.nextInt(previous.size()));
                            if (!inputs.contains(input)) {
                                inputs.add(input);
                            }
                        }
                        fake.transform("t" + transformations++, inputs, List.of(name));
                        inputs.forEach(input -> children.get(input).add(name));
                    }
                }
            }
        }
    }

    private static Map<String, Integer> distances(Map<String, Set<String>> adjacency, String from, int limit) {
        Map<String, Integer> distance = new LinkedHashMap<>();
        distance.put(from, 0);
        List<String> frontier = List.of(from);
        for (int level = 1; level <= limit && !frontier.isEmpty(); level++) {
            List<String> next = new ArrayList<>();
            for (String node : frontier) {
                for (String neighbor : adjacency.getOrDefault(node, Set.of())) {
                    if (distance.putIfAbsent(neighbor, level) == null) {
                        next.add(neighbor);
                    }
                }
            }
            frontier = next;
        }
        return distance;
    }

    @Test
    void theLevelSynchronousAnalysisEqualsTheNaiveOneOnRandomGraphs() {
        int compared = 0;
        int withAffected = 0;
        int truncatedCases = 0;
        for (long seed = 0; seed < 400; seed++) {
            RandomGraph random = new RandomGraph(seed, 3 + (int) (seed % 13), 4);
            Random picker = new Random(seed * 31 + 7);
            String failed = random.nodes.get(picker.nextInt(random.nodes.size()));
            ImpactAnalyzer analyzer = new ImpactAnalyzer(random.fake);
            Map<UUID, String> byId = new HashMap<>();
            random.nodes.forEach(name -> byId.put(id(name), name));

            ImpactResult actual = analyzer.analyze(id(failed));

            // el oráculo, aparte: un recorrido por punto de partida, sin atajos
            Map<String, Set<String>> parents = new LinkedHashMap<>();
            random.children.forEach((parent, kids) -> kids.forEach(kid -> parents.computeIfAbsent(kid, key -> new HashSet<>()).add(parent)));
            Map<String, Integer> backward = distances(parents, failed, 10);
            Map<String, String> bestStart = new LinkedHashMap<>();
            Map<String, Map<String, Integer>> reach = new LinkedHashMap<>();
            for (String start : backward.keySet()) {
                reach.put(start, distances(random.children, start, 10));
            }
            for (String start : backward.keySet()) {
                for (String node : reach.get(start).keySet()) {
                    String current = bestStart.get(node);
                    boolean better = current == null || backward.get(start) < backward.get(current)
                            || backward.get(start).equals(backward.get(current)) && id(start).toString().compareTo(id(current).toString()) < 0;
                    if (better) {
                        bestStart.put(node, start);
                    }
                }
            }
            Map<String, String> expected = new TreeMap<>();
            bestStart.forEach((node, start) -> {
                if (!node.equals(failed) && random.phase.get(node) == BatchPhase.AVAILABLE && random.quality.get(node) != QualityStatus.FAILED) {
                    expected.put(node, start);
                }
            });
            // truncado: quedaba un antecesor más allá de 10 niveles o un nodo que un punto de partida alcanzaría en el nivel 11 y mejoraría lo que ya se sabe
            boolean truncated = distances(parents, failed, 11).containsValue(11);
            for (String start : backward.keySet()) {
                Map<String, Integer> deep = distances(random.children, start, 11);
                for (Map.Entry<String, Integer> entry : deep.entrySet()) {
                    if (entry.getValue() == 11) {
                        String current = bestStart.get(entry.getKey());
                        boolean better = current == null || backward.get(start) < backward.get(current)
                                || backward.get(start).equals(backward.get(current)) && id(start).toString().compareTo(id(current).toString()) < 0;
                        truncated |= better;
                    }
                }
            }

            Map<String, String> got = new TreeMap<>();
            actual.affected().forEach(a -> got.put(byId.get(a.batchId()), byId.get(a.commonAncestorBatchId())));
            assertThat(got).as("semilla %d, lote fallado %s", seed, failed).isEqualTo(expected);
            assertThat(actual.truncated()).as("semilla %d, truncado", seed).isEqualTo(truncated);
            assertThat(actual.affected()).extracting(AffectedBatch::companyId).as("semilla %d: la empresa de cada afectado", seed)
                    .containsExactlyElementsOf(actual.affected().stream().map(a -> random.company.get(byId.get(a.batchId()))).toList());
            compared++;
            withAffected += expected.isEmpty() ? 0 : 1;
            truncatedCases += truncated ? 1 : 0;
        }
        assertThat(compared).isEqualTo(400);
        assertThat(withAffected).as("la mayoría de los casos tiene afectados: la prueba no es vacía").isGreaterThan(200);
        assertThat(truncatedCases).as("y hay casos que pasan del límite de niveles").isGreaterThan(5);
    }
}
