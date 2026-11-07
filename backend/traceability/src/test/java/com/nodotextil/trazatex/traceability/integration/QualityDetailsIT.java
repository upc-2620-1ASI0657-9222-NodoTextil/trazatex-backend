package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.traceability.application.dto.ApplyFailureCauseCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyQualityTestsCommand;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchPhaseChangedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchSplitUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyPrivacyUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyRegisteredUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyQualityDetailsUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransferAcceptedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransformationCompletedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.QueryLineageUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.RegisterBatchUseCase;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleBatch;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleQualityTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nodotextil.trazatex.traceability.integration.GraphBuilder.id;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los detalles de calidad (valores de las pruebas y causa de falla) por el puerto de entrada {@link ApplyQualityDetailsUseCase} y su adaptador de Neo4j: se guardan
 * en el lote, gana el evento más reciente y la genealogía los muestra a la dueña y, a otra empresa, solo si se compartió {@code QUALITY_TEST_VALUES} o
 * {@code FAILURE_CAUSE}.
 */
class QualityDetailsIT extends IntegrationTestBase {

    static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    static final Instant T1 = Instant.parse("2026-10-23T10:00:00Z");
    static final ApplyQualityTestsCommand.TestValue RESISTENCIA = new ApplyQualityTestsCommand.TestValue("Resistencia", "28", "23", "cN/tex", "FAILED");
    static final ApplyQualityTestsCommand.TestValue HUMEDAD = new ApplyQualityTestsCommand.TestValue("Humedad", "8", "7", "%", "CONFORMING");

    @Autowired
    ApplyQualityDetailsUseCase details;
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
        graph.company(A, "Andina SAC").company(B, "Bahía SAC");
    }

    private VisibleBatch seen(String batch, UUID requester) {
        return queries.lineage(id(batch), requester).nodes().stream().filter(VisibleBatch.class::isInstance).map(VisibleBatch.class::cast)
                .filter(b -> b.id().equals(id(batch))).findFirst().orElseThrow();
    }

    /** Lo que la genealogía devuelve de esas pruebas. */
    private static List<VisibleQualityTest> visible(ApplyQualityTestsCommand.TestValue... values) {
        return java.util.Arrays.stream(values).map(t -> new VisibleQualityTest(t.criterionName(), t.expectedValue(), t.obtainedValue(), t.unit(), t.testResult())).toList();
    }

    private void tests(String batch, Instant at, ApplyQualityTestsCommand.TestValue... values) {
        details.applyTests(new ApplyQualityTestsCommand(UUID.randomUUID(), id(batch), at, List.of(values)));
    }

    /** Una causa de una falla confirmada: la única que el grafo muestra. */
    private void cause(String batch, Instant at, String cause) {
        cause(batch, at, "CONFIRMED", cause);
    }

    private void cause(String batch, Instant at, String status, String cause) {
        details.applyFailureCause(new ApplyFailureCauseCommand(UUID.randomUUID(), id(batch), at, status, cause));
    }

    @Test
    void theOwnerSeesTheTestsAndTheCauseAndAnotherCompanyOnlyWhatWasShared() {
        graph.batch("L1", A);
        tests("L1", T1, RESISTENCIA, HUMEDAD);
        cause("L1", T1, "Fibra corta");

        assertThat(seen("L1", A).qualityTests()).containsExactlyElementsOf(visible(RESISTENCIA, HUMEDAD));
        assertThat(seen("L1", A).failureCause()).isEqualTo("Fibra corta");
        assertThat(seen("L1", B).qualityTests()).as("sin configuración: privado").isNull();
        assertThat(seen("L1", B).failureCause()).isNull();

        graph.privacy(A, Map.of("QUALITY_TEST_VALUES", "SHARED"));
        assertThat(seen("L1", B).qualityTests()).containsExactlyElementsOf(visible(RESISTENCIA, HUMEDAD));
        assertThat(seen("L1", B).failureCause()).isNull();

        graph.privacy(A, Map.of("FAILURE_CAUSE", "SHARED"));
        assertThat(seen("L1", B).qualityTests()).isNull();
        assertThat(seen("L1", B).failureCause()).isEqualTo("Fibra corta");
    }

    /** I15: una causa solo es visible mientras la falla está confirmada; abierta o resuelta, ni la propia empresa ni otra la ven. */
    @Test
    void theCauseIsOnlyShownWhileTheFailureIsConfirmed() {
        graph.batch("L1", A);
        graph.privacy(A, Map.of("FAILURE_CAUSE", "SHARED"));

        cause("L1", T1, "OPEN", "Fibra corta");
        assertThat(seen("L1", A).failureCause()).as("abierta: todavía es una hipótesis").isEmpty();
        assertThat(seen("L1", B).failureCause()).as("otra empresa, falla abierta").isEmpty();

        cause("L1", T1.plusSeconds(60), "CONFIRMED", "Fibra corta");
        assertThat(seen("L1", A).failureCause()).isEqualTo("Fibra corta");
        assertThat(seen("L1", B).failureCause()).as("compartida y confirmada").isEqualTo("Fibra corta");

        cause("L1", T1.plusSeconds(120), "RESOLVED", "Fibra corta");
        assertThat(seen("L1", B).failureCause()).as("resuelta: el lote ya se liberó").isEmpty();

        cause("L1", T1.plusSeconds(180), "", "Fibra corta");
        assertThat(seen("L1", B).failureCause()).as("sin estado: nunca se muestra").isEmpty();
    }

    @Test
    void theMostRecentEventWinsAndOneThatArrivesLateDoesNotOverwriteIt() {
        graph.batch("L1", A);
        tests("L1", T1.plusSeconds(60), HUMEDAD);
        cause("L1", T1.plusSeconds(60), "Hilo mal torcido");

        tests("L1", T1, RESISTENCIA);
        cause("L1", T1, "Fibra corta");
        assertThat(seen("L1", A).qualityTests()).as("llegó tarde con un instante anterior").containsExactlyElementsOf(visible(HUMEDAD));
        assertThat(seen("L1", A).failureCause()).isEqualTo("Hilo mal torcido");

        tests("L1", T1.plusSeconds(120), RESISTENCIA, HUMEDAD);
        cause("L1", T1.plusSeconds(120), "");
        assertThat(seen("L1", A).qualityTests()).containsExactlyElementsOf(visible(RESISTENCIA, HUMEDAD));
        assertThat(seen("L1", A).failureCause()).isEmpty();
    }

    @Test
    void detailsThatArriveBeforeTheBatchLeaveItProvisionalAndSurviveItsRegistration() {
        tests("L1", T1, RESISTENCIA);
        cause("L1", T1, "Fibra corta");
        assertThat(neo4j.query("MATCH (b:Batch {batchId: $id}) RETURN b.provisional AS p").bind(id("L1").toString()).to("id").fetch().one().orElseThrow().get("p"))
                .isEqualTo(true);

        graph.batch("L1", A);

        assertThat(seen("L1", A).qualityTests()).containsExactlyElementsOf(visible(RESISTENCIA));
        assertThat(seen("L1", A).failureCause()).isEqualTo("Fibra corta");
    }
}
