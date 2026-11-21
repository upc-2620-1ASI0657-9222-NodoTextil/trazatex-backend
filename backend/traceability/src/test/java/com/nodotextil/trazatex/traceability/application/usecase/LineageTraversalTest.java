package com.nodotextil.trazatex.traceability.application.usecase;

import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import com.nodotextil.trazatex.traceability.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.traceability.domain.model.EdgeType;
import com.nodotextil.trazatex.traceability.domain.model.LineageDirection;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleBatch;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleLineage;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleNode;
import com.nodotextil.trazatex.traceability.domain.service.LineageAuthorizationPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.nodotextil.trazatex.traceability.application.usecase.FakeLineageReadPort.COMPANY_A;
import static com.nodotextil.trazatex.traceability.application.usecase.FakeLineageReadPort.COMPANY_B;
import static com.nodotextil.trazatex.traceability.application.usecase.FakeLineageReadPort.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LineageTraversalTest {

    private final FakeLineageReadPort graph = new FakeLineageReadPort();
    private final LineageQueryService service = new LineageQueryService(graph, new LineageAuthorizationPolicy());

    private static Map<String, Integer> levels(VisibleLineage lineage) {
        return lineage.nodes().stream().collect(Collectors.toMap(node -> label(node), VisibleNode::level));
    }

    private static String label(VisibleNode node) {
        return node instanceof VisibleBatch batch ? batch.traceabilityId().substring(3) : "T:" + node.id().toString().substring(0, 4);
    }

    private static List<String> edges(VisibleLineage lineage, Map<UUID, String> names) {
        return lineage.edges().stream().map(e -> e.type() + " " + names.get(e.from()) + ">" + names.get(e.to())).sorted().toList();
    }

    private Map<UUID, String> nameOf(String... batches) {
        Map<UUID, String> names = new java.util.HashMap<>();
        for (String batch : batches) {
            names.put(id(batch), batch);
        }
        graph.transformations.keySet().forEach(t -> names.put(t, "T:" + t.toString().substring(0, 4)));
        return names;
    }

    private String t(String name) {
        return "T:" + id(name).toString().substring(0, 4);
    }

    @Test
    void ancestorsOfASplitChainAreListedLevelByLevelWithTheirRelationships() {
        graph.batch("A", "B", "C").split("A", "B").split("B", "C");

        VisibleLineage result = service.ancestors(id("C"), COMPANY_A);

        assertThat(result.direction()).isEqualTo(LineageDirection.ANCESTORS);
        assertThat(result.maxLevels()).isEqualTo(10);
        assertThat(levels(result)).containsExactlyInAnyOrderEntriesOf(Map.of("C", 0, "B", -1, "A", -2));
        assertThat(edges(result, nameOf("A", "B", "C"))).containsExactly("SPLIT_INTO A>B", "SPLIT_INTO B>C");
        assertThat(result.complete()).isTrue();
        assertThat(result.truncated()).isFalse();
        assertThat(result.nodes()).filteredOn(VisibleBatch.class::isInstance).extracting(n -> ((VisibleBatch) n).transferHistory()).containsOnlyNulls();
    }

    @Test
    void descendantsOfASplitTreeShowTheBranches() {
        graph.batch("A", "B", "C", "D").split("A", "B", "C").split("B", "D");

        VisibleLineage result = service.descendants(id("A"), COMPANY_A);

        assertThat(result.direction()).isEqualTo(LineageDirection.DESCENDANTS);
        assertThat(levels(result)).containsExactlyInAnyOrderEntriesOf(Map.of("A", 0, "B", 1, "C", 1, "D", 2));
        assertThat(edges(result, nameOf("A", "B", "C", "D"))).containsExactly("SPLIT_INTO A>B", "SPLIT_INTO A>C", "SPLIT_INTO B>D");
    }

    @Test
    void aTransformationIsOneLevelAndAppearsAsANodeWithItsRelationships() {
        graph.batch("X", "Y", "Z").transform("T", List.of("X", "Y"), List.of("Z"));

        VisibleLineage back = service.ancestors(id("Z"), COMPANY_A);
        VisibleLineage forward = service.descendants(id("X"), COMPANY_A);

        assertThat(levels(back)).containsExactlyInAnyOrderEntriesOf(Map.of("Z", 0, "X", -1, "Y", -1, t("T"), -1));
        assertThat(edges(back, nameOf("X", "Y", "Z"))).containsExactly("INPUT_OF X>" + t("T"), "INPUT_OF Y>" + t("T"), "PRODUCED " + t("T") + ">Z");
        assertThat(levels(forward)).containsExactlyInAnyOrderEntriesOf(Map.of("X", 0, "Z", 1, t("T"), 1));
        assertThat(forward.nodes()).filteredOn(n -> n.kind().name().equals("TRANSFORMATION")).hasSize(1);
    }

    @Test
    void aDiamondOfDivisionsAndMixesVisitsEveryNodeOnce() {
        graph.batch("A", "B", "C", "D").split("A", "B", "C").transform("T", List.of("B", "C"), List.of("D"));

        VisibleLineage back = service.ancestors(id("D"), COMPANY_A);
        VisibleLineage forward = service.descendants(id("A"), COMPANY_A);

        assertThat(back.nodes()).hasSize(5);
        assertThat(levels(back)).containsEntry("A", -2).containsEntry("D", 0);
        assertThat(edges(back, nameOf("A", "B", "C", "D"))).containsExactly("INPUT_OF B>" + t("T"), "INPUT_OF C>" + t("T"), "PRODUCED " + t("T") + ">D",
                "SPLIT_INTO A>B", "SPLIT_INTO A>C");
        assertThat(forward.nodes()).hasSize(5);
        assertThat(edges(forward, nameOf("A", "B", "C", "D"))).isEqualTo(edges(back, nameOf("A", "B", "C", "D")));
    }

    @Test
    void theLevelOfANodeIsItsShortestDistance() {
        graph.batch("A", "B", "C", "D").split("A", "B", "C").split("C", "D").split("D", "B");

        VisibleLineage result = service.descendants(id("A"), COMPANY_A);

        assertThat(levels(result)).containsEntry("B", 1).containsEntry("D", 2);
        assertThat(edges(result, nameOf("A", "B", "C", "D"))).contains("SPLIT_INTO D>B");
    }

    @Test
    void aGenealogyOfExactlyTenLevelsIsNotTruncated() {
        graph.chain("n", 11);

        VisibleLineage result = service.ancestors(id("n10"), COMPANY_A);

        assertThat(result.nodes()).hasSize(11);
        assertThat(levels(result)).containsEntry("n10", 0).containsEntry("n0", -10);
        assertThat(result.truncated()).isFalse();
        assertThat(result.complete()).isTrue();
    }

    @Test
    void aGenealogyOfElevenLevelsIsCutAtTenAndSaysSo() {
        graph.chain("n", 12);

        VisibleLineage back = service.ancestors(id("n11"), COMPANY_A);
        VisibleLineage forward = service.descendants(id("n0"), COMPANY_A);

        assertThat(back.nodes()).hasSize(11);
        assertThat(levels(back)).containsEntry("n1", -10).doesNotContainKey("n0");
        assertThat(back.truncated()).isTrue();
        assertThat(back.complete()).as("truncated y complete son cosas distintas").isTrue();
        assertThat(levels(forward)).containsEntry("n10", 10).doesNotContainKey("n11");
        assertThat(forward.truncated()).isTrue();
    }

    @Test
    void aTransformationHopCountsAsOneLevelForTheLimit() {
        graph.batch("m0");
        for (int i = 1; i <= 11; i++) {
            graph.batch("m" + i);
            if (i % 2 == 0) {
                graph.split("m" + (i - 1), "m" + i);
            } else {
                graph.transform("T" + i, List.of("m" + (i - 1)), List.of("m" + i));
            }
        }

        VisibleLineage result = service.ancestors(id("m11"), COMPANY_A);

        assertThat(result.nodes().stream().filter(n -> n.kind().name().equals("BATCH"))).hasSize(11);
        assertThat(levels(result)).containsEntry("m1", -10).doesNotContainKey("m0");
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void anEdgeBetweenTwoIncludedNodesAtTheLimitIsKeptAndIsNotATruncation() {
        graph.chain("p", 11).batch("S", "q").split("p0", "q").split("p1", "S").split("q", "S");
        graph.split("q", "p10");
        FakeLineageReadPort long_ = new FakeLineageReadPort();
        long_.batch("S", "q").chain("p", 10);
        long_.split("p9", "S");
        long_.split("q", "S").split("q", "p0");
        LineageQueryService local = new LineageQueryService(long_, new LineageAuthorizationPolicy());

        VisibleLineage result = local.ancestors(id("S"), COMPANY_A);

        assertThat(levels(result)).containsEntry("p0", -10).containsEntry("q", -1);
        assertThat(result.edges()).as("la arista q → p0 une dos nodos incluidos").anyMatch(e -> e.type() == EdgeType.SPLIT_INTO && e.from().equals(id("q")) && e.to().equals(id("p0")));
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void theTraversalStopsAtAnIncompleteNodeAndDoesNotCrossIt() {
        graph.batch("A", "B", "D", "E").provisional("C").split("A", "B").split("B", "C").split("C", "D").split("D", "E");

        VisibleLineage back = service.ancestors(id("E"), COMPANY_A);
        VisibleLineage forward = service.descendants(id("A"), COMPANY_A);

        assertThat(levels(back)).containsExactlyInAnyOrderEntriesOf(Map.of("E", 0, "D", -1));
        assertThat(back.complete()).isFalse();
        assertThat(back.truncated()).isFalse();
        assertThat(back.edges()).as("ninguna arista hacia el nodo incompleto").hasSize(1);
        assertThat(levels(forward)).containsExactlyInAnyOrderEntriesOf(Map.of("A", 0, "B", 1));
        assertThat(forward.complete()).isFalse();
    }

    @Test
    void anIncompleteBranchDoesNotHideTheCompleteOneOfADiamond() {
        graph.batch("A", "B", "D").provisional("C").split("A", "B", "C").transform("T", List.of("B", "C"), List.of("D"));

        VisibleLineage result = service.ancestors(id("D"), COMPANY_A);

        assertThat(levels(result)).containsEntry("B", -1).containsEntry("A", -2).doesNotContainKey("C");
        assertThat(result.complete()).isFalse();
        assertThat(result.edges()).as("ninguna arista hacia el nodo incompleto").noneMatch(edge -> edge.from().equals(id("C")) || edge.to().equals(id("C")));
    }

    @Test
    void anIncompleteTransformationStopsTheTraversalToo() {
        graph.batch("X", "Z").transform("T", COMPANY_A, List.of("X"), List.of("Z"), true);

        VisibleLineage result = service.ancestors(id("Z"), COMPANY_A);

        assertThat(result.nodes()).hasSize(1);
        assertThat(result.complete()).isFalse();
    }

    @Test
    void aBatchWithMissingMandatoryDataIsIncompleteEvenIfNotMarkedProvisional() {
        graph.batch("A", "B").split("A", "B");
        graph.replace("A", new com.nodotextil.trazatex.traceability.domain.model.graph.BatchData(id("A"), "TT-A", null, COMPANY_A, "Algodón", null, null, false, false,
                FakeLineageReadPort.T0, null, null, null, null, null, false));

        VisibleLineage result = service.ancestors(id("B"), COMPANY_A);

        assertThat(result.nodes()).hasSize(1);
        assertThat(result.complete()).isFalse();
    }

    @Test
    void anUnknownAndAnIncompleteStartingBatchAreIndistinguishable() {
        graph.provisional("P");

        assertThatThrownBy(() -> service.ancestors(id("P"), COMPANY_A)).isInstanceOf(NotFoundException.class)
                .satisfies(e -> assertThat(((NotFoundException) e).code()).isEqualTo(ErrorCodes.BATCH_NOT_FOUND));
        assertThatThrownBy(() -> service.lineage(id("no existe"), COMPANY_A)).isInstanceOf(NotFoundException.class)
                .satisfies(e -> assertThat(((NotFoundException) e).code()).isEqualTo(ErrorCodes.BATCH_NOT_FOUND))
                .hasMessage("El lote no existe.");
        assertThatThrownBy(() -> service.descendants(id("P"), COMPANY_A)).hasMessage("El lote no existe.");
    }

    @Test
    void theCompleteViewJoinsBothDirectionsWithTheTransferHistoryAndTheOtherViewsDoNotCarryIt() {
        graph.batch("A", "B", "C").split("A", "B").split("B", "C").companyName(COMPANY_A, "Hilandería Andina SAC").companyName(COMPANY_B, "Tintorería del Sur SAC")
                .transferred("B", COMPANY_B, "TR1");

        VisibleLineage complete = service.lineage(id("B"), COMPANY_A);
        VisibleLineage back = service.ancestors(id("B"), COMPANY_A);

        assertThat(complete.direction()).isEqualTo(LineageDirection.BOTH);
        assertThat(levels(complete)).containsExactlyInAnyOrderEntriesOf(Map.of("A", -1, "B", 0, "C", 1));
        VisibleBatch middle = (VisibleBatch) complete.nodes().stream().filter(n -> n.id().equals(id("B"))).findFirst().orElseThrow();
        assertThat(middle.transferHistory()).singleElement().satisfies(entry -> {
            assertThat(entry.transferId()).isEqualTo(id("TR1"));
            assertThat(entry.toCompanyId()).isEqualTo(COMPANY_B);
            assertThat(entry.toBusinessName()).isEqualTo("Tintorería del Sur SAC");
        });
        assertThat(middle.businessName()).isEqualTo("Hilandería Andina SAC");
        assertThat(complete.nodes()).filteredOn(VisibleBatch.class::isInstance).filteredOn(n -> !n.id().equals(id("B")))
                .extracting(n -> ((VisibleBatch) n).transferHistory()).as("los lotes sin transferencias no traen historial").containsOnly(List.of());
        assertThat(back.nodes()).filteredOn(VisibleBatch.class::isInstance).extracting(n -> ((VisibleBatch) n).transferHistory()).containsOnlyNulls();
    }

    @Test
    void inTheCompleteViewTruncatedIsTrueIfEitherSideIsCutAndCompleteOnlyIfBothAre() {
        graph.chain("n", 13);
        graph.chain("m", 13).provisional("y").split("m12", "y");

        VisibleLineage cutBack = service.lineage(id("n11"), COMPANY_A);
        VisibleLineage incompleteForward = service.lineage(id("m12"), COMPANY_A);

        assertThat(cutBack.truncated()).isTrue();
        assertThat(cutBack.complete()).isTrue();
        assertThat(incompleteForward.complete()).as("hacia adelante se topó con un nodo incompleto").isFalse();
        assertThat(incompleteForward.truncated()).as("hacia atrás son 12 saltos: también cortado").isTrue();
    }

    @Test
    void aGraphWithManyMoreCrossingPathsThanNodesIsWalkedWithAFixedNumberOfLevelQueries() {
        graph.batch("a0", "b0");
        for (int layer = 1; layer <= 10; layer++) {
            graph.batch("a" + layer, "b" + layer);
            graph.split("a" + (layer - 1), "a" + layer, "b" + layer).split("b" + (layer - 1), "a" + layer, "b" + layer);
        }

        VisibleLineage back = service.ancestors(id("a10"), COMPANY_A);
        int parentCalls = graph.parentCalls;
        VisibleLineage forward = service.descendants(id("a0"), COMPANY_A);

        assertThat(back.nodes()).as("22 lotes, no 1024 caminos").hasSize(21);
        assertThat(back.edges()).hasSize(4 * 10 - 2);
        assertThat(parentCalls).as("una consulta por nivel (10) más la que comprueba si hay algo pasado el límite").isLessThanOrEqualTo(11);
        assertThat(graph.childCalls).isLessThanOrEqualTo(11);
        assertThat(forward.nodes()).hasSize(21);
        assertThat(graph.batchLookups).as("el lote de partida se busca una vez por consulta, no uno por nodo").isEqualTo(2);
    }

    @Test
    void aCorruptedCyclicGraphDoesNotHangTheTraversal() {
        graph.batch("A", "B").split("A", "B").split("B", "A");

        VisibleLineage result = service.ancestors(id("A"), COMPANY_A);

        assertThat(levels(result)).containsExactlyInAnyOrderEntriesOf(Map.of("A", 0, "B", -1));
        assertThat(result.truncated()).isFalse();
    }
}
