package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchPhaseChangedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchSplitUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyPrivacyUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyRegisteredUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransferAcceptedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransformationCompletedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.QueryLineageUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.RegisterBatchUseCase;
import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.EdgeType;
import com.nodotextil.trazatex.traceability.domain.model.LineageDirection;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;
import com.nodotextil.trazatex.traceability.domain.model.view.Access;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleBatch;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleBatchPage;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleBatchSummary;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleLineage;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleNode;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleTransformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.nodotextil.trazatex.traceability.integration.GraphBuilder.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las consultas de genealogía (US30–US32, US36) sobre Neo4j real, con el grafo armado por los mismos casos de uso que usan los consumidores. */
class LineageQueryIT extends IntegrationTestBase {

    static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @Autowired
    QueryLineageUseCase queries;
    @Autowired
    RegisterBatchUseCase register;
    @Autowired
    ApplyBatchSplitUseCase split;
    @Autowired
    ApplyTransformationCompletedUseCase transformation;
    @Autowired
    ApplyBatchPhaseChangedUseCase phase;
    @Autowired
    ApplyTransferAcceptedUseCase transfer;
    @Autowired
    ApplyCompanyRegisteredUseCase company;
    @Autowired
    ApplyCompanyPrivacyUseCase privacy;

    GraphBuilder graph;

    @BeforeEach
    void builder() {
        graph = new GraphBuilder(register, split, transformation, phase, transfer, company, privacy);
        graph.company(A, "Hilandería Andina SAC").company(B, "Tintorería del Sur SAC");
    }

    private static Map<String, Integer> levels(VisibleLineage lineage) {
        return lineage.nodes().stream().collect(Collectors.toMap(node -> node instanceof VisibleBatch batch ? batch.traceabilityId().substring(3) : "T:" + node.kind(), VisibleNode::level, (a, b) -> a));
    }

    private static VisibleBatch batch(VisibleLineage lineage, String name) {
        return lineage.nodes().stream().filter(VisibleBatch.class::isInstance).map(VisibleBatch.class::cast).filter(b -> b.id().equals(id(name))).findFirst().orElseThrow();
    }

    private static List<String> edges(VisibleLineage lineage) {
        return lineage.edges().stream().map(e -> e.type() + " " + e.from().toString().substring(0, 6) + ">" + e.to().toString().substring(0, 6)).sorted().toList();
    }

    // ---- datos y privacidad ----

    @Test
    void theOwnCompanySeesEveryFieldOfItsBatchesAndTheNamesComeFromCompanyRegistered() {
        graph.batch("L1", A).phase("L1", A, BatchPhase.AVAILABLE, true, 3).split("L1", A, "H1");

        VisibleLineage view = queries.ancestors(id("H1"), A);

        VisibleBatch parent = batch(view, "L1");
        assertThat(parent.access()).isEqualTo(Access.OWN);
        assertThat(parent.businessName()).isEqualTo("Hilandería Andina SAC");
        assertThat(parent.traceabilityId()).isEqualTo("TT-L1");
        assertThat(parent.qrCode()).isEqualTo("QR-L1");
        assertThat(parent.materialType()).isEqualTo("Algodón");
        assertThat(parent.phase()).isEqualTo(BatchPhase.AVAILABLE);
        assertThat(parent.qualityStatus()).isEqualTo(QualityStatus.NOT_REVIEWED);
        assertThat(parent.createdAt()).isNotNull();
        assertThat(parent.finalProduct()).isFalse();
        assertThat(parent.supplierName()).isEqualTo("Textiles del Norte");
        assertThat(parent.origin()).isEqualTo("Piura");
        assertThat(parent.quantityKg()).isEqualByComparingTo("100");
        assertThat(parent.composition()).singleElement().satisfies(part -> {
            assertThat(part.name()).isEqualTo("Algodón");
            assertThat(part.percentage()).isEqualByComparingTo("100");
        });
        assertThat(parent.receptionCharacteristics()).isEqualTo("Fibra limpia");
        assertThat(parent.qualityHold()).as("la retención por calidad la ve su empresa").isTrue();
    }

    @Test
    void anotherCompanySeesOnlyTheAlwaysSharedFieldsAndNeverTheQualityHold() {
        graph.batch("L1", A).phase("L1", A, BatchPhase.AVAILABLE, true, 3).split("L1", A, "H1");

        VisibleLineage view = queries.lineage(id("H1"), B);

        for (String name : List.of("L1", "H1")) {
            VisibleBatch seen = batch(view, name);
            assertThat(seen.access()).isEqualTo(Access.SHARED);
            assertThat(seen.traceabilityId()).isEqualTo("TT-" + name);
            assertThat(seen.businessName()).isEqualTo("Hilandería Andina SAC");
            assertThat(seen.supplierName()).isNull();
            assertThat(seen.origin()).isNull();
            assertThat(seen.quantityKg()).isNull();
            assertThat(seen.composition()).isNull();
            assertThat(seen.receptionCharacteristics()).isNull();
            assertThat(seen.qualityHold()).isNull();
        }
        assertThat(view.edges()).as("las relaciones siempre se muestran").hasSize(1);
    }

    @Test
    void theTransformationOfAnotherCompanyKeepsItsTypeAndDatesButHidesTheMachine() {
        graph.batches(A, "X", "Y").transform("T", A, List.of("X", "Y"), "Z");

        VisibleTransformation foreign = (VisibleTransformation) queries.ancestors(id("Z"), B).nodes().stream().filter(VisibleTransformation.class::isInstance).findFirst().orElseThrow();
        VisibleTransformation own = (VisibleTransformation) queries.ancestors(id("Z"), A).nodes().stream().filter(VisibleTransformation.class::isInstance).findFirst().orElseThrow();

        assertThat(foreign.type()).isNotNull();
        assertThat(foreign.startedAt()).isNotNull();
        assertThat(foreign.machineName()).isNull();
        assertThat(foreign.wasteReason()).isNull();
        assertThat(own.machineName()).isEqualTo("Cardadora");
        assertThat(own.machineType()).isEqualTo("Cardado");
        assertThat(own.wasteReason()).isEqualTo("Orillo");
        assertThat(own.wasteKg()).isEqualByComparingTo("1");
    }

    // ---- recorrido ----

    @Test
    void aDiamondOfDivisionsAndMixesIsWalkedBothWays() {
        graph.batch("A0", A).split("A0", A, "B0", "C0").transform("T", A, List.of("B0", "C0"), "D0");

        VisibleLineage back = queries.ancestors(id("D0"), A);
        VisibleLineage forward = queries.descendants(id("A0"), A);

        assertThat(back.nodes()).hasSize(5);
        assertThat(levels(back)).containsEntry("A0", -2).containsEntry("D0", 0).containsEntry("B0", -1);
        assertThat(back.edges()).hasSize(5).extracting(e -> e.type()).containsExactlyInAnyOrder(EdgeType.SPLIT_INTO, EdgeType.SPLIT_INTO, EdgeType.INPUT_OF, EdgeType.INPUT_OF, EdgeType.PRODUCED);
        assertThat(forward.nodes()).hasSize(5);
        assertThat(edges(forward)).isEqualTo(edges(back));
        assertThat(back.complete()).isTrue();
        assertThat(back.truncated()).isFalse();
    }

    @Test
    void aGenealogyOfTenLevelsIsNotCutAndOneOfElevenIs() {
        graph.chain("n", A, 12); // n0 → … → n11

        VisibleLineage tenLevels = queries.ancestors(id("n10"), A);
        VisibleLineage elevenLevels = queries.ancestors(id("n11"), A);
        VisibleLineage forward = queries.descendants(id("n0"), A);

        assertThat(tenLevels.nodes()).hasSize(11);
        assertThat(tenLevels.truncated()).isFalse();
        assertThat(levels(tenLevels)).containsEntry("n0", -10);
        assertThat(elevenLevels.nodes()).hasSize(11);
        assertThat(levels(elevenLevels)).doesNotContainKey("n0").containsEntry("n1", -10);
        assertThat(elevenLevels.truncated()).isTrue();
        assertThat(elevenLevels.complete()).isTrue();
        assertThat(forward.truncated()).isTrue();
        assertThat(levels(forward)).containsEntry("n10", 10).doesNotContainKey("n11");
    }

    @Test
    void theTraversalStopsAtABatchWhoseRegistrationHasNotArrivedAndSaysSo() {
        // la transformación nombra una entrada que aún no se registró: queda provisional
        graph.batch("OK", A).transform("T", A, List.of("OK", "TARDIO"), "Z");

        VisibleLineage view = queries.ancestors(id("Z"), A);

        assertThat(levels(view)).containsEntry("OK", -1).doesNotContainKey("TARDIO");
        assertThat(view.nodes().stream().map(VisibleNode::id)).doesNotContain(id("TARDIO"));
        assertThat(view.edges()).noneMatch(e -> e.from().equals(id("TARDIO")) || e.to().equals(id("TARDIO")));
        assertThat(view.complete()).isFalse();
        assertThat(view.truncated()).isFalse();

        graph.batch("TARDIO", A); // llega su registro: la genealogía se completa sola

        VisibleLineage completed = queries.ancestors(id("Z"), A);
        assertThat(levels(completed)).containsEntry("TARDIO", -1);
        assertThat(completed.complete()).isTrue();
    }

    @Test
    void aProvisionalOrUnknownStartingBatchIsNotFoundWithoutSayingWhich() {
        graph.transform("T", A, List.of("FANTASMA"), "Z"); // FANTASMA solo existe como entrada provisional

        assertThatThrownBy(() -> queries.lineage(id("FANTASMA"), A)).isInstanceOf(NotFoundException.class).hasMessage("El lote no existe.");
        assertThatThrownBy(() -> queries.lineage(UUID.randomUUID(), A)).isInstanceOf(NotFoundException.class).hasMessage("El lote no existe.");
    }

    @Test
    void aBatchWhoseStoredPhaseIsUnknownIsTreatedAsIncompleteAndHidden() {
        graph.chain("n", A, 3);
        neo4j.query("MATCH (b:Batch {batchId: $id}) SET b.phase = 'FLOTANDO'").bind(id("n1").toString()).to("id").run();

        VisibleLineage view = queries.ancestors(id("n2"), A);

        assertThat(view.nodes()).hasSize(1);
        assertThat(view.complete()).isFalse();
        assertThatThrownBy(() -> queries.ancestors(id("n1"), A)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void theCompleteViewCarriesEveryBatchTransferHistoryWithTheNameOfTheReceivingCompany() {
        graph.batch("L1", A).split("L1", A, "H1", "H2").transferred("TR1", "H1", B).transferred("TR2", "H1", A);

        VisibleLineage view = queries.lineage(id("L1"), B);
        VisibleLineage back = queries.descendants(id("L1"), B);

        assertThat(batch(view, "H1").transferHistory()).extracting(t -> t.transferId(), t -> t.toBusinessName()).containsExactly(
                org.assertj.core.groups.Tuple.tuple(id("TR1"), "Tintorería del Sur SAC"), org.assertj.core.groups.Tuple.tuple(id("TR2"), "Hilandería Andina SAC"));
        assertThat(batch(view, "H2").transferHistory()).isEmpty();
        assertThat(batch(view, "L1").transferHistory()).isEmpty();
        assertThat(back.direction()).isEqualTo(LineageDirection.DESCENDANTS);
        assertThat(back.nodes()).filteredOn(VisibleBatch.class::isInstance).extracting(n -> ((VisibleBatch) n).transferHistory()).containsOnlyNulls();
    }

    // ---- búsqueda ----

    @Test
    void theSearchFindsByQrByExactTraceabilityIdAndByPrefixOnlyAmongTheOwnBatches() {
        graph.batches(A, "AAA1", "AAA2", "AAB1").batches(B, "AAA9", "ZZZ1");

        VisibleBatchPage byQr = queries.search(A, "QR-AAA9", null, null, null);
        VisibleBatchPage prefix = queries.search(A, null, "TT-", null, null);
        VisibleBatchPage foreignExact = queries.search(A, null, "TT-AAA9", null, null);
        VisibleBatchPage foreignPrefix = queries.search(A, null, "TT-AAA", null, null);

        assertThat(byQr.items()).singleElement().satisfies(item -> {
            assertThat(item.access()).isEqualTo(Access.SHARED);
            assertThat(item.businessName()).isEqualTo("Tintorería del Sur SAC");
        });
        assertThat(prefix.items()).extracting(VisibleBatchSummary::traceabilityId).as("TT- solo lista los lotes de mi empresa").containsExactlyInAnyOrder("TT-AAA1", "TT-AAA2", "TT-AAB1");
        assertThat(foreignExact.items()).extracting(VisibleBatchSummary::traceabilityId).containsExactly("TT-AAA9");
        assertThat(foreignPrefix.items()).extracting(VisibleBatchSummary::traceabilityId).as("el prefijo no alcanza el lote ajeno AAA9").containsExactlyInAnyOrder("TT-AAA1", "TT-AAA2");
        assertThat(queries.search(B, null, "TT-", null, null).items()).extracting(VisibleBatchSummary::traceabilityId).containsExactlyInAnyOrder("TT-AAA9", "TT-ZZZ1");
    }

    @Test
    void theSearchIsPaginatedNewestFirstWithAStableOrderAndTheCap() {
        for (int i = 0; i < 25; i++) {
            graph.batch(String.format("N%02d", i), A);
        }

        VisibleBatchPage first = queries.search(A, null, "TT-N", 0, 10);
        VisibleBatchPage second = queries.search(A, null, "TT-N", 1, 10);
        VisibleBatchPage third = queries.search(A, null, "TT-N", 2, 10);
        VisibleBatchPage all = queries.search(A, null, "TT-N", 0, 100_000);

        assertThat(first.totalElements()).isEqualTo(25);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.items()).hasSize(10);
        assertThat(second.items()).hasSize(10);
        assertThat(third.items()).hasSize(5);
        assertThat(first.items().get(0).traceabilityId()).as("el más reciente primero").isEqualTo("TT-N24");
        List<String> paged = new java.util.ArrayList<>();
        first.items().forEach(i -> paged.add(i.traceabilityId()));
        second.items().forEach(i -> paged.add(i.traceabilityId()));
        third.items().forEach(i -> paged.add(i.traceabilityId()));
        assertThat(paged).doesNotHaveDuplicates().hasSize(25);
        assertThat(all.size()).isEqualTo(100);
        assertThat(all.items().stream().map(VisibleBatchSummary::traceabilityId).toList()).isEqualTo(paged);
    }

    @Test
    void theSearchNeverReturnsIncompleteBatches() {
        graph.batch("AAA1", A).batch("AAA2", A);
        neo4j.query("MATCH (b:Batch {batchId: $id}) REMOVE b.phase").bind(id("AAA2").toString()).to("id").run(); // un dato obligatorio ausente
        neo4j.query("CREATE (:Batch {batchId: $id, provisional: true})").bind(UUID.randomUUID().toString()).to("id").run();

        assertThat(queries.search(A, null, "TT-AAA", null, null).items()).extracting(VisibleBatchSummary::traceabilityId).containsExactly("TT-AAA1");
        assertThat(queries.search(A, "QR-AAA2", null, null, null).items()).isEmpty();
        assertThat(queries.search(B, null, "TT-AAA2", null, null).items()).isEmpty();
        assertThat(queries.search(A, null, "TT-AAA", null, null).totalElements()).isEqualTo(1);
    }

    @Test
    void aGraphWithMoreThanAThousandCrossingPathsIsAnsweredQuickly() {
        graph.batches(A, "a0", "b0");
        for (int layer = 1; layer <= 10; layer++) {
            graph.split("a" + (layer - 1), A, "a" + layer, "b" + layer);
            graph.split("b" + (layer - 1), A, "a" + layer, "b" + layer);
        }

        long start = System.nanoTime();
        VisibleLineage back = queries.ancestors(id("a10"), A);
        VisibleLineage forward = queries.descendants(id("a0"), A);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(back.nodes()).hasSize(21);
        assertThat(forward.nodes()).hasSize(21);
        assertThat(elapsed).as("no enumera los 1024 caminos").isLessThan(Duration.ofSeconds(3));
    }
}
