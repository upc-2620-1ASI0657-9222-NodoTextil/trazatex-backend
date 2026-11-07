package com.nodotextil.trazatex.traceability.domain.service;

import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.CompanyPrivacy;
import com.nodotextil.trazatex.traceability.domain.model.CompositionComponent;
import com.nodotextil.trazatex.traceability.domain.model.ConfigurableField;
import com.nodotextil.trazatex.traceability.domain.model.EdgeType;
import com.nodotextil.trazatex.traceability.domain.model.LineageDirection;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;
import com.nodotextil.trazatex.traceability.domain.model.TransformationType;
import com.nodotextil.trazatex.traceability.domain.model.Visibility;
import com.nodotextil.trazatex.traceability.domain.model.graph.BatchData;
import com.nodotextil.trazatex.traceability.domain.model.graph.GraphEdge;
import com.nodotextil.trazatex.traceability.domain.model.graph.Lineage;
import com.nodotextil.trazatex.traceability.domain.model.graph.TransferRecord;
import com.nodotextil.trazatex.traceability.domain.model.graph.TransformationData;
import com.nodotextil.trazatex.traceability.domain.model.view.Access;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleBatch;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleBatchSummary;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleLineage;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleTransformation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La política de privacidad B2B (ADR-006, RNF-07): lo de la propia empresa sale completo; de otra empresa, solo lo siempre compartido y
 * lo que esa empresa marcó {@code SHARED}; {@code qualityHold} solo para la dueña; las aristas y el historial de transferencias siempre.
 */
class LineageAuthorizationPolicyTest {

    private static final UUID MINE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final Instant T0 = Instant.parse("2026-10-21T10:00:00Z");

    /** Un lote con todos sus datos; su nombre libre ({@code name}) es {@code "Lote <name>"}. */
    private static BatchData batch(String name, UUID company, boolean hold) {
        return new BatchData(UUID.nameUUIDFromBytes(name.getBytes()), "TT-" + name, "QR-" + name, company, "Algodón", BatchPhase.AVAILABLE, QualityStatus.CONFORMING,
                hold, false, T0, "Textiles del Norte", "Piura", new BigDecimal("12.500"), List.of(new CompositionComponent("Algodón", new BigDecimal("100"))),
                "Fibra limpia", false, null, List.of(), "Lote " + name);
    }

    private static TransformationData transformation(String name, UUID company) {
        return new TransformationData(UUID.nameUUIDFromBytes(name.getBytes()), company, TransformationType.DYEING, T0, T0.plusSeconds(3600), "Cardadora", "Cardado",
                new BigDecimal("3.000"), new BigDecimal("2.000"), "Orillo", false);
    }

    private static Lineage lineage(LineageDirection direction, List<BatchData> batches, List<TransformationData> transformations, List<GraphEdge> edges,
                                   Map<UUID, List<TransferRecord>> transfers) {
        return new Lineage(batches.get(0).batchId(), direction, batches.stream().map(b -> new Lineage.LeveledBatch(b, 0)).toList(),
                transformations.stream().map(t -> new Lineage.LeveledTransformation(t, 1)).toList(), edges, transfers,
                Map.of(MINE, "Hilandería Andina SAC", OTHER, "Tintorería del Sur SAC"), Map.of(), true, false);
    }

    private static PolicyUnderTest.Lookup sharing(ConfigurableField... fields) {
        Map<ConfigurableField, Visibility> map = new EnumMap<>(ConfigurableField.class);
        for (ConfigurableField field : fields) {
            map.put(field, Visibility.SHARED);
        }
        return company -> CompanyPrivacy.of(map);
    }

    private static VisibleBatch only(VisibleLineage view) {
        return (VisibleBatch) view.nodes().get(0);
    }

    // ---- lotes ----

    @Test
    void qualityValuesAndFailureCauseRespectTheirSeparatePrivacySettings() {
        BatchData base = batch("QUALITY", OTHER, true);
        var values = List.of(new com.nodotextil.trazatex.traceability.domain.model.graph.QualityTestValue("Resistencia", "28", "23", "cN/tex", "FAILED"));
        BatchData data = new BatchData(base.batchId(),base.traceabilityId(),base.qrCode(),base.companyId(),base.materialType(),base.phase(),
                base.qualityStatus(),base.qualityHold(),base.finalProduct(),base.createdAt(),base.supplierName(),base.origin(),base.quantityKg(),
                base.composition(),base.receptionCharacteristics(),false,"Fibra corta",values);
        Lineage graph = lineage(LineageDirection.BOTH,List.of(data),List.of(),List.of(),Map.of());

        VisibleBatch hidden = only(new PolicyUnderTest(PolicyUnderTest.Lookup.allPrivate()).view(graph,MINE));
        VisibleBatch shared = only(new PolicyUnderTest(sharing(ConfigurableField.QUALITY_TEST_VALUES)).view(graph,MINE));
        VisibleBatch own = only(new PolicyUnderTest(PolicyUnderTest.Lookup.allPrivate()).view(graph,OTHER));

        assertThat(hidden.qualityTests()).isNull();
        assertThat(hidden.failureCause()).isNull();
        var seenValues = List.of(new com.nodotextil.trazatex.traceability.domain.model.view.VisibleQualityTest("Resistencia", "28", "23", "cN/tex", "FAILED"));
        assertThat(shared.qualityTests()).containsExactlyElementsOf(seenValues);
        assertThat(shared.failureCause()).isNull();
        assertThat(own.failureCause()).isEqualTo("Fibra corta");
        assertThat(own.qualityTests()).containsExactlyElementsOf(seenValues);
    }

    @Test
    void theOwnCompanySeesItsBatchCompleteIncludingTheQualityHold() {
        VisibleBatch seen = only(new PolicyUnderTest(PolicyUnderTest.Lookup.allPrivate())
                .view(lineage(LineageDirection.ANCESTORS, List.of(batch("A", MINE, true)), List.of(), List.of(), Map.of()), MINE));

        assertThat(seen.access()).isEqualTo(Access.OWN);
        assertThat(seen.supplierName()).isEqualTo("Textiles del Norte");
        assertThat(seen.origin()).isEqualTo("Piura");
        assertThat(seen.quantityKg()).isEqualByComparingTo("12.5");
        assertThat(seen.composition()).hasSize(1);
        assertThat(seen.receptionCharacteristics()).isEqualTo("Fibra limpia");
        assertThat(seen.qualityHold()).isTrue();
        assertThat(seen.businessName()).isEqualTo("Hilandería Andina SAC");
        assertThat(seen.name()).isEqualTo("Lote A");
    }

    @Test
    void anotherCompanysBatchShowsOnlyTheAlwaysSharedFieldsWhenNothingWasConfigured() {
        VisibleBatch seen = only(new PolicyUnderTest(PolicyUnderTest.Lookup.allPrivate())
                .view(lineage(LineageDirection.ANCESTORS, List.of(batch("A", OTHER, true)), List.of(), List.of(), Map.of()), MINE));

        assertThat(seen.access()).isEqualTo(Access.SHARED);
        assertThat(seen.traceabilityId()).isEqualTo("TT-A");
        assertThat(seen.qrCode()).isEqualTo("QR-A");
        assertThat(seen.companyId()).isEqualTo(OTHER);
        assertThat(seen.businessName()).isEqualTo("Tintorería del Sur SAC");
        assertThat(seen.materialType()).isEqualTo("Algodón");
        assertThat(seen.phase()).isEqualTo(BatchPhase.AVAILABLE);
        assertThat(seen.qualityStatus()).isEqualTo(QualityStatus.CONFORMING);
        assertThat(seen.createdAt()).isEqualTo(T0);
        assertThat(seen.finalProduct()).isFalse();
        assertThat(seen.supplierName()).isNull();
        assertThat(seen.origin()).isNull();
        assertThat(seen.quantityKg()).isNull();
        assertThat(seen.composition()).isNull();
        assertThat(seen.receptionCharacteristics()).isNull();
        assertThat(seen.qualityHold()).as("qualityHold es un dato interno: solo la dueña").isNull();
        assertThat(seen.name()).as("name es texto libre: solo la dueña").isNull();
    }

    /** Cada campo configurable destapa exactamente sus datos y ningún otro. */
    @ParameterizedTest
    @EnumSource(ConfigurableField.class)
    void eachConfigurableFieldRevealsExactlyItsOwnDataOfAnotherCompanysBatchAndTransformation(ConfigurableField field) {
        VisibleLineage view = new PolicyUnderTest(sharing(field)).view(lineage(LineageDirection.ANCESTORS, List.of(batch("A", OTHER, true)),
                List.of(transformation("T", OTHER)), List.of(), Map.of()), MINE);
        VisibleBatch batch = only(view);
        VisibleTransformation transformation = (VisibleTransformation) view.nodes().get(1);

        Set<String> revealedInBatch = new TreeSet<>();
        add(revealedInBatch, "supplierName", batch.supplierName());
        add(revealedInBatch, "origin", batch.origin());
        add(revealedInBatch, "quantityKg", batch.quantityKg());
        add(revealedInBatch, "composition", batch.composition());
        add(revealedInBatch, "receptionCharacteristics", batch.receptionCharacteristics());
        add(revealedInBatch, "name", batch.name());
        Set<String> revealedInTransformation = new TreeSet<>();
        add(revealedInTransformation, "machineName", transformation.machineName());
        add(revealedInTransformation, "machineType", transformation.machineType());
        add(revealedInTransformation, "shrinkageKg", transformation.shrinkageKg());
        add(revealedInTransformation, "wasteKg", transformation.wasteKg());
        add(revealedInTransformation, "wasteReason", transformation.wasteReason());

        assertThat(revealedInBatch).isEqualTo(switch (field) {
            case SUPPLIER -> Set.of("supplierName");
            case ORIGIN -> Set.of("origin");
            case QUANTITY -> Set.of("quantityKg");
            case COMPOSITION -> Set.of("composition");
            case RECEPTION_CHARACTERISTICS -> Set.of("receptionCharacteristics");
            default -> Set.of(); // MACHINERY, SHRINKAGE, WASTE, WASTE_REASON, FAILURE_CAUSE y QUALITY_TEST_VALUES no son del lote
        }).as("ningún campo configurable destapa name").doesNotContain("name");
        assertThat(revealedInTransformation).isEqualTo(switch (field) {
            case MACHINERY -> Set.of("machineName", "machineType");
            case SHRINKAGE -> Set.of("shrinkageKg");
            case WASTE -> Set.of("wasteKg");
            case WASTE_REASON -> Set.of("wasteReason");
            default -> Set.of();
        });
        assertThat(batch.qualityHold()).as("ningún campo compartido destapa qualityHold").isNull();
    }

    private static void add(Set<String> set, String name, Object value) {
        if (value != null) {
            set.add(name);
        }
    }

    @Test
    void sharingEveryConfigurableFieldStillNeverRevealsTheQualityHoldToAnotherCompany() {
        VisibleBatch seen = only(new PolicyUnderTest(sharing(ConfigurableField.values()))
                .view(lineage(LineageDirection.ANCESTORS, List.of(batch("A", OTHER, true)), List.of(), List.of(), Map.of()), MINE));

        assertThat(seen.supplierName()).isNotNull();
        assertThat(seen.origin()).isNotNull();
        assertThat(seen.quantityKg()).isNotNull();
        assertThat(seen.composition()).isNotNull();
        assertThat(seen.receptionCharacteristics()).isNotNull();
        assertThat(seen.qualityHold()).isNull();
        assertThat(seen.name()).as("compartir todo tampoco destapa name").isNull();
    }

    // ---- name: el nombre libre del lote, solo para la empresa dueña (OWN) ----

    private static VisibleBatch node(VisibleLineage view, BatchData batch) {
        return view.nodes().stream().filter(VisibleBatch.class::isInstance).map(VisibleBatch.class::cast)
                .filter(b -> b.id().equals(batch.batchId())).findFirst().orElseThrow();
    }

    /** Vista hacia atrás, hacia adelante y completa: OWN lo ve; SHARED no, ni sin configuración (privado) ni compartiendo todos los configurables. */
    @ParameterizedTest
    @EnumSource(LineageDirection.class)
    void theBatchNameIsVisibleOnlyToItsOwnerInEveryDirection(LineageDirection direction) {
        BatchData mine = batch("MIO", MINE, false);
        BatchData theirs = batch("AJENO", OTHER, false);
        Lineage graph = lineage(direction, List.of(mine, theirs), List.of(), List.of(), Map.of());

        VisibleLineage privateView = new PolicyUnderTest(PolicyUnderTest.Lookup.allPrivate()).view(graph, MINE);
        VisibleLineage sharedView = new PolicyUnderTest(sharing(ConfigurableField.values())).view(graph, MINE);

        assertThat(node(privateView, mine).access()).isEqualTo(Access.OWN);
        assertThat(node(privateView, mine).name()).as("OWN").isEqualTo("Lote MIO");
        assertThat(node(sharedView, mine).name()).as("OWN").isEqualTo("Lote MIO");
        assertThat(node(privateView, theirs).access()).isEqualTo(Access.SHARED);
        assertThat(node(privateView, theirs).name()).as("SHARED de una empresa sin nada compartido (privado)").isNull();
        assertThat(node(sharedView, theirs).access()).isEqualTo(Access.SHARED);
        assertThat(node(sharedView, theirs).name()).as("SHARED de una empresa que comparte todos los configurables").isNull();
        assertThat(node(sharedView, theirs).supplierName()).as("control: lo configurable sí se compartió").isNotNull();
    }

    @Test
    void theOtherCompanySeesItsOwnBatchNameAndNotMine() {
        BatchData mine = batch("MIO", MINE, false);
        BatchData theirs = batch("AJENO", OTHER, false);
        VisibleLineage view = new PolicyUnderTest(sharing(ConfigurableField.values()))
                .view(lineage(LineageDirection.BOTH, List.of(mine, theirs), List.of(), List.of(), Map.of()), OTHER);

        assertThat(node(view, theirs).name()).isEqualTo("Lote AJENO");
        assertThat(node(view, mine).name()).isNull();
    }

    @Test
    void theOwnCompanysPrivacySettingsDoNotHideItsOwnData() {
        VisibleBatch seen = only(new PolicyUnderTest(PolicyUnderTest.Lookup.allPrivate())
                .view(lineage(LineageDirection.ANCESTORS, List.of(batch("A", MINE, false)), List.of(), List.of(), Map.of()), MINE));

        assertThat(seen.supplierName()).isNotNull();
        assertThat(seen.qualityHold()).isFalse();
    }

    // ---- transformaciones, aristas e historial ----

    @Test
    void aTransformationOfAnotherCompanyKeepsItsTypeAndDatesButHidesTheMachineAndTheLosses() {
        VisibleTransformation seen = (VisibleTransformation) new PolicyUnderTest(PolicyUnderTest.Lookup.allPrivate())
                .view(lineage(LineageDirection.ANCESTORS, List.of(batch("A", OTHER, false)), List.of(transformation("T", OTHER)), List.of(), Map.of()), MINE).nodes().get(1);

        assertThat(seen.type()).isEqualTo(TransformationType.DYEING);
        assertThat(seen.startedAt()).isEqualTo(T0);
        assertThat(seen.completedAt()).isEqualTo(T0.plusSeconds(3600));
        assertThat(seen.companyId()).isEqualTo(OTHER);
        assertThat(seen.access()).isEqualTo(Access.SHARED);
        assertThat(seen.machineName()).isNull();
        assertThat(seen.machineType()).isNull();
        assertThat(seen.shrinkageKg()).isNull();
        assertThat(seen.wasteKg()).isNull();
        assertThat(seen.wasteReason()).isNull();
    }

    @Test
    void theOwnTransformationIsCompleteAndTheEdgesAlwaysShow() {
        BatchData a = batch("A", OTHER, false);
        TransformationData t = transformation("T", MINE);
        List<GraphEdge> edges = List.of(new GraphEdge(EdgeType.INPUT_OF, a.batchId(), t.transformationId()), new GraphEdge(EdgeType.PRODUCED, t.transformationId(), a.batchId()));

        VisibleLineage view = new PolicyUnderTest(PolicyUnderTest.Lookup.allPrivate()).view(lineage(LineageDirection.ANCESTORS, List.of(a), List.of(t), edges, Map.of()), MINE);

        VisibleTransformation own = (VisibleTransformation) view.nodes().get(1);
        assertThat(own.machineName()).isEqualTo("Cardadora");
        assertThat(own.wasteReason()).isEqualTo("Orillo");
        assertThat(view.edges()).extracting(e -> e.type()).containsExactly(EdgeType.INPUT_OF, EdgeType.PRODUCED);
    }

    @Test
    void theTransferHistoryIsVisibleToEveryoneInTheCompleteViewEvenForAnotherCompanysBatch() {
        BatchData a = batch("A", OTHER, false);
        Map<UUID, List<TransferRecord>> transfers = Map.of(a.batchId(), List.of(new TransferRecord(UUID.randomUUID(), a.batchId(), MINE, T0.plusSeconds(60))));

        VisibleBatch seen = only(new PolicyUnderTest(PolicyUnderTest.Lookup.allPrivate()).view(lineage(LineageDirection.BOTH, List.of(a), List.of(), List.of(), transfers), MINE));

        assertThat(seen.transferHistory()).singleElement().satisfies(entry -> {
            assertThat(entry.toCompanyId()).isEqualTo(MINE);
            assertThat(entry.toBusinessName()).isEqualTo("Hilandería Andina SAC");
            assertThat(entry.at()).isEqualTo(T0.plusSeconds(60));
        });
    }

    @Test
    void aBatchWithoutTransfersHasAnEmptyHistoryInTheCompleteViewAndNoneInTheOthers() {
        BatchData a = batch("A", MINE, false);
        PolicyUnderTest policy = new PolicyUnderTest(PolicyUnderTest.Lookup.allPrivate());

        assertThat(only(policy.view(lineage(LineageDirection.BOTH, List.of(a), List.of(), List.of(), Map.of()), MINE)).transferHistory()).isEmpty();
        assertThat(only(policy.view(lineage(LineageDirection.ANCESTORS, List.of(a), List.of(), List.of(), Map.of()), MINE)).transferHistory()).isNull();
        assertThat(only(policy.view(lineage(LineageDirection.DESCENDANTS, List.of(a), List.of(), List.of(), Map.of()), MINE)).transferHistory()).isNull();
    }

    @Test
    void theFlagsAndTheLevelLimitTravelUnchanged() {
        Lineage raw = new Lineage(UUID.randomUUID(), LineageDirection.ANCESTORS, List.of(new Lineage.LeveledBatch(batch("A", MINE, false), 0)), List.of(), List.of(), Map.of(),
                Map.of(), Map.of(), false, true);

        VisibleLineage view = new PolicyUnderTest(PolicyUnderTest.Lookup.allPrivate()).view(raw, MINE);

        assertThat(view.complete()).isFalse();
        assertThat(view.truncated()).isTrue();
        assertThat(view.maxLevels()).isEqualTo(10);
        assertThat(view.nodes().stream().map(n -> n.level()).collect(Collectors.toList())).containsExactly(0);
    }

    // ---- resúmenes de búsqueda ----

    @Test
    void aSearchResultHasTheSharedFieldsAndTheAccessAndNeverTheConfigurableOnesOrTheHold() {
        PolicyUnderTest policy = new PolicyUnderTest(sharing(ConfigurableField.values()));

        VisibleBatchSummary own = policy.summary(batch("A", MINE, true), "Hilandería Andina SAC", MINE);
        VisibleBatchSummary foreign = policy.summary(batch("B", OTHER, true), "Tintorería del Sur SAC", MINE);

        assertThat(own.access()).isEqualTo(Access.OWN);
        assertThat(foreign.access()).isEqualTo(Access.SHARED);
        assertThat(foreign.businessName()).isEqualTo("Tintorería del Sur SAC");
        assertThat(foreign.traceabilityId()).isEqualTo("TT-B");
        assertThat(java.util.Arrays.stream(VisibleBatchSummary.class.getRecordComponents()).map(c -> c.getName()))
                .as("el resumen no tiene campos configurables ni qualityHold")
                .containsExactly("batchId", "traceabilityId", "qrCode", "companyId", "businessName", "materialType", "phase", "qualityStatus", "createdAt", "finalProduct", "access");
    }
}
