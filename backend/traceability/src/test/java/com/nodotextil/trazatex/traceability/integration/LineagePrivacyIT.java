package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchPhaseChangedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchSplitUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyPrivacyUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyRegisteredUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransferAcceptedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransformationCompletedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.QueryLineageUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.RegisterBatchUseCase;
import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.ConfigurableField;
import com.nodotextil.trazatex.traceability.domain.model.view.Access;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleBatch;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleLineage;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleTransformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static com.nodotextil.trazatex.traceability.integration.GraphBuilder.id;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * US36 / ADR-006 sobre Neo4j real: la privacidad B2B de cada empresa (el mapa de su último {@code CompanyPrivacyUpdated}) decide qué campos configurables
 * de sus nodos ve otra empresa. Se aplica a <b>todos sus nodos, también los antiguos</b>, desde que llega el evento; cada empresa con su propia configuración;
 * lo que no se configuró (o no se puede leer) es {@code PRIVATE}; la empresa dueña siempre lo ve todo; {@code qualityHold} nunca sale a otra.
 */
class LineagePrivacyIT extends IntegrationTestBase {

    static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    static final UUID C = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    static final UUID D = UUID.fromString("00000000-0000-0000-0000-0000000000d4");

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
        graph.company(A, "Andina SAC").company(B, "Bahía SAC").company(C, "Cumbre SAC").company(D, "Delta SAC");
        // la empresa B: un lote registrado (con proveedor, origen, cantidad, composición y recepción), con retención por calidad, y una transformación que lo usa
        graph.batch("BB", B).phase("BB", B, BatchPhase.AVAILABLE, true, 2).transform("TB", B, List.of("BB"), "BO");
    }

    /** Qué campos configurables de los nodos de B (el lote registrado BB y la transformación TB) ve quien consulta, por nombre de atributo. */
    private Set<String> visibleFieldsOfB(UUID requester) {
        VisibleLineage view = queries.ancestors(id("BO"), requester);
        Set<String> visible = new TreeSet<>();
        for (var node : view.nodes()) {
            if (node instanceof VisibleBatch batch && batch.id().equals(id("BB"))) {
                add(visible, "supplierName", batch.supplierName());
                add(visible, "origin", batch.origin());
                add(visible, "quantityKg", batch.quantityKg());
                add(visible, "composition", batch.composition());
                add(visible, "receptionCharacteristics", batch.receptionCharacteristics());
                add(visible, "qualityHold", batch.qualityHold());
            }
            if (node instanceof VisibleTransformation t && t.id().equals(id("TB"))) {
                add(visible, "machineName", t.machineName());
                add(visible, "machineType", t.machineType());
                add(visible, "shrinkageKg", t.shrinkageKg());
                add(visible, "wasteKg", t.wasteKg());
                add(visible, "wasteReason", t.wasteReason());
            }
        }
        return visible;
    }

    private static void add(Set<String> visible, String name, Object value) {
        if (value != null) {
            visible.add(name);
        }
    }

    private static final Set<String> EVERYTHING_CONFIGURABLE = new TreeSet<>(List.of("supplierName", "origin", "quantityKg", "composition", "receptionCharacteristics",
            "machineName", "machineType", "shrinkageKg", "wasteKg", "wasteReason"));

    private static Map<String, String> sharing(ConfigurableField... shared) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        for (ConfigurableField field : ConfigurableField.values()) {
            map.put(field.name(), "PRIVATE");
        }
        for (ConfigurableField field : shared) {
            map.put(field.name(), "SHARED");
        }
        return map;
    }

    private static Map<ConfigurableField, Set<String>> dataOf() {
        Map<ConfigurableField, Set<String>> data = new EnumMap<>(ConfigurableField.class);
        data.put(ConfigurableField.SUPPLIER, Set.of("supplierName"));
        data.put(ConfigurableField.ORIGIN, Set.of("origin"));
        data.put(ConfigurableField.QUANTITY, Set.of("quantityKg"));
        data.put(ConfigurableField.COMPOSITION, Set.of("composition"));
        data.put(ConfigurableField.RECEPTION_CHARACTERISTICS, Set.of("receptionCharacteristics"));
        data.put(ConfigurableField.MACHINERY, Set.of("machineName", "machineType"));
        data.put(ConfigurableField.SHRINKAGE, Set.of("shrinkageKg"));
        data.put(ConfigurableField.WASTE, Set.of("wasteKg"));
        data.put(ConfigurableField.WASTE_REASON, Set.of("wasteReason"));
        data.put(ConfigurableField.FAILURE_CAUSE, Set.of());      // sin dato en el grafo todavía (Quality, siguiente sprint)
        data.put(ConfigurableField.QUALITY_TEST_VALUES, Set.of()); // ídem
        return data;
    }

    // ---- campo por campo ----

    @Test
    void withoutAnyConfigurationEverythingConfigurableIsPrivateAndTheQualityHoldIsInternal() {
        assertThat(visibleFieldsOfB(A)).isEmpty();
        assertThat(visibleFieldsOfB(B)).as("la dueña lo ve todo, incluida la retención").containsAll(EVERYTHING_CONFIGURABLE).contains("qualityHold");
    }

    @ParameterizedTest
    @EnumSource(ConfigurableField.class)
    void sharingOneFieldRevealsExactlyTheDataOfThatFieldAndNothingElse(ConfigurableField field) {
        graph.privacy(B, sharing(field));

        assertThat(visibleFieldsOfB(A)).isEqualTo(dataOf().get(field));
    }

    @Test
    void sharingEverythingRevealsEveryConfigurableFieldButNeverTheQualityHold() {
        graph.privacy(B, sharing(ConfigurableField.values()));

        assertThat(visibleFieldsOfB(A)).isEqualTo(EVERYTHING_CONFIGURABLE).doesNotContain("qualityHold");
    }

    @Test
    void theOwnerSeesEverythingWhateverItsOwnConfigurationIs() {
        graph.privacy(B, sharing());

        assertThat(visibleFieldsOfB(B)).containsAll(EVERYTHING_CONFIGURABLE).contains("qualityHold");
    }

    // ---- desde que llega, a todos los nodos, también los antiguos ----

    @Test
    void aChangeAppliesToEveryNodeOfTheCompanyIncludingTheOldOnesFromTheMomentItArrivesAndOnlyThen() {
        assertThat(visibleFieldsOfB(A)).as("antes de que llegue: privado").isEmpty();

        graph.privacy(B, sharing(ConfigurableField.SUPPLIER, ConfigurableField.MACHINERY));
        assertThat(visibleFieldsOfB(A)).as("BB y TB ya existían: se aplica a los antiguos").containsExactlyInAnyOrder("supplierName", "machineName", "machineType");

        graph.batch("BN", B).transform("TN", B, List.of("BN"), "BN2"); // nodos nuevos, creados con la configuración ya vigente
        VisibleLineage fresh = queries.ancestors(id("BN2"), A);
        assertThat(fresh.nodes().stream().filter(VisibleBatch.class::isInstance).map(VisibleBatch.class::cast).filter(b -> b.id().equals(id("BN"))).findFirst().orElseThrow()
                .supplierName()).as("los nuevos también").isEqualTo("Textiles del Norte");

        graph.privacy(B, sharing(ConfigurableField.ORIGIN));
        assertThat(visibleFieldsOfB(A)).as("el último mapa reemplaza al anterior: el proveedor y la máquina vuelven a ser privados").containsExactly("origin");

        graph.privacy(B, sharing());
        assertThat(visibleFieldsOfB(A)).as("todo PRIVATE otra vez, también lo que ya se había visto").isEmpty();
        assertThat(queries.ancestors(id("BN2"), A).nodes().stream().filter(VisibleBatch.class::isInstance).map(VisibleBatch.class::cast).allMatch(b -> b.supplierName() == null)).isTrue();
    }

    @Test
    void aFieldThatIsMissingFromTheLatestMapIsPrivate() {
        graph.privacy(B, Map.of("SUPPLIER", "SHARED", "ORIGIN", "SHARED"));
        assertThat(visibleFieldsOfB(A)).containsExactlyInAnyOrder("supplierName", "origin");

        graph.privacy(B, Map.of("SUPPLIER", "SHARED"));

        assertThat(visibleFieldsOfB(A)).containsExactly("supplierName");
    }

    // ---- cada empresa con la suya ----

    @Test
    void eachCompanyHasItsOwnConfigurationAndOneCompanysSharingNeverLeaksAnothers() {
        graph.batch("CC", C).batch("DD", D);
        graph.transform("TA", A, List.of("BB", "CC", "DD"), "AO");
        graph.privacy(B, sharing(ConfigurableField.SUPPLIER)).privacy(C, sharing(ConfigurableField.ORIGIN, ConfigurableField.QUANTITY));

        VisibleLineage view = queries.ancestors(id("AO"), A);

        VisibleBatch fromB = batch(view, "BB");
        VisibleBatch fromC = batch(view, "CC");
        VisibleBatch fromD = batch(view, "DD");
        assertThat(fromB.supplierName()).isNotNull();
        assertThat(fromB.origin()).isNull();
        assertThat(fromB.quantityKg()).isNull();
        assertThat(fromC.supplierName()).isNull();
        assertThat(fromC.origin()).isNotNull();
        assertThat(fromC.quantityKg()).isNotNull();
        assertThat(fromD.supplierName()).as("D no configuró nada: privado").isNull();
        assertThat(fromD.origin()).isNull();
        assertThat(fromB.access()).isEqualTo(Access.SHARED);
        assertThat(batch(queries.ancestors(id("AO"), C), "CC").supplierName()).as("C ve sus propios nodos completos").isNotNull();
    }

    private static VisibleBatch batch(VisibleLineage view, String name) {
        List<VisibleBatch> found = new ArrayList<>();
        view.nodes().stream().filter(VisibleBatch.class::isInstance).map(VisibleBatch.class::cast).filter(b -> b.id().equals(id(name))).forEach(found::add);
        return found.getFirst();
    }

    // ---- lo que no se puede leer no se comparte ----

    @Test
    void whatIsStoredButCannotBeReadSharesNothing() {
        for (String broken : List.of("esto no es json", "[1,2,3]", "{\"SUPPLIER\":", "null")) {
            neo4j.query("MATCH (c:Company {companyId: $company}) SET c.privacy = $privacy").bind(B.toString()).to("company").bind(broken).to("privacy").run();

            assertThat(visibleFieldsOfB(A)).as("privacidad ilegible: %s", broken).isEmpty();
        }
    }

    @Test
    void anUnknownFieldIsIgnoredAnUnknownValueIsPrivateAndTheRestStillApplies() {
        graph.privacy(B, Map.of("SUPPLIER", "SHARED", "INVENTADO", "SHARED", "ORIGIN", "QUIZAS"));

        assertThat(visibleFieldsOfB(A)).containsExactly("supplierName");
    }

    @Test
    void aCompanyThatNeverConfiguredAnythingAndAnUnknownOneAreBothPrivate() {
        graph.batch("ZZ", UUID.fromString("00000000-0000-0000-0000-0000000000e5")); // una empresa que ni siquiera tiene nodo :Company

        VisibleBatch seen = batch(queries.lineage(id("ZZ"), A), "ZZ");

        assertThat(seen.supplierName()).isNull();
        assertThat(seen.quantityKg()).isNull();
        assertThat(seen.access()).isEqualTo(Access.SHARED);
    }
}
