package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.traceability.application.dto.AnalyzeFailureImpactCommand;
import com.nodotextil.trazatex.traceability.application.port.in.AnalyzeFailureImpactUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchPhaseChangedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchSplitUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyPrivacyUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyRegisteredUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransferAcceptedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransformationCompletedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.RegisterBatchUseCase;
import com.nodotextil.trazatex.traceability.domain.exception.ImpactAnalysisIncompleteException;
import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nodotextil.trazatex.traceability.integration.GraphBuilder.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El análisis de impacto (US33) sobre Neo4j real: el resultado queda en el outbox <b>en la misma transacción</b> que el {@code :ProcessedEvent} del
 * {@code FailureConfirmed}, una sola vez por falla, con el payload exacto del catálogo y los lotes de todas las empresas; una familia incompleta no deja nada; y el
 * costo en consultas depende de los niveles (2 por nivel, 10 + 1 por dirección) y no de los nodos.
 */
@Import(QueryCounting.Config.class)
class FailureImpactIT extends IntegrationTestBase {

    static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @Autowired
    AnalyzeFailureImpactUseCase impact;
    @Autowired
    IdempotentEventHandler handler;
    @Autowired
    ObjectMapper json;
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
    }

    /** F (de A) se dividió en F1 y F2; F1 se mezcló con X (de B) y dio U (de B); F1 quedó procesado. Afectados: F2 (de A) y U (de B), ambos hijos de F. */
    private void aFamilyOfTwoCompanies() {
        graph.batch("F", A).split("F", A, "F1", "F2").batch("X", B).transform("T", B, List.of("F1", "X"), "U");
        graph.phase("F1", A, BatchPhase.PROCESSED, false, 2);
    }

    private AnalyzeFailureImpactCommand command(UUID failure, String batch) {
        return new AnalyzeFailureImpactCommand(UUID.randomUUID(), failure, id(batch));
    }

    private List<Map<String, Object>> outbox() {
        return new ArrayList<>(neo4j.query("MATCH (o:OutboxEvent) RETURN properties(o) AS p ORDER BY o.createdAt").fetch().all().stream().map(row -> {
            @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) row.get("p");
            return p;
        }).toList());
    }

    private long count(String label) {
        return (Long) neo4j.query("MATCH (n:" + label + ") RETURN count(n) AS n").fetch().one().orElseThrow().get("n");
    }

    // ---- el resultado en el outbox ----

    @Test
    void theResultIsInTheOutboxWithTheExactPayloadOfTheCatalogAndTheBatchesOfEveryCompany() {
        aFamilyOfTwoCompanies();
        UUID failure = UUID.randomUUID();

        impact.apply(command(failure, "F"));

        assertThat(outbox()).hasSize(1);
        Map<String, Object> row = outbox().getFirst();
        assertThat(row).containsEntry("topic", "traceability.impact").containsEntry("eventType", "PossibleDerivedFailureDetected")
                .containsEntry("aggregateId", failure.toString()).containsEntry("status", "PENDING");
        JsonNode envelope = json.readTree((String) row.get("payload"));
        assertThat(envelope.propertyNames()).containsExactlyInAnyOrder("eventId", "type", "aggregateId", "version", "occurredAt", "payload");
        assertThat(envelope.get("type").asString()).isEqualTo("PossibleDerivedFailureDetected");
        assertThat(envelope.get("aggregateId").asString()).as("la clave del mensaje es la falla").isEqualTo(failure.toString());
        JsonNode payload = envelope.get("payload");
        assertThat(payload.propertyNames()).containsExactlyInAnyOrder("failureId", "sourceBatchId", "affected");
        assertThat(payload.get("failureId").asString()).isEqualTo(failure.toString());
        assertThat(payload.get("sourceBatchId").asString()).isEqualTo(id("F").toString());
        List<String> order = new ArrayList<>();
        for (JsonNode affected : payload.get("affected")) {
            assertThat(affected.propertyNames()).containsExactlyInAnyOrder("batchId", "companyId", "commonAncestorBatchId");
            assertThat(affected.get("commonAncestorBatchId").asString()).isEqualTo(id("F").toString());
            order.add(affected.get("batchId").asString());
            assertThat(affected.get("companyId").asString()).isEqualTo(affected.get("batchId").asString().equals(id("F2").toString()) ? A.toString() : B.toString());
        }
        assertThat(order).containsExactlyInAnyOrder(id("F2").toString(), id("U").toString()).isSorted();
    }

    @Test
    void theAnalysisSeesTheBatchesOfOtherCompaniesWhateverTheirPrivacy() {
        aFamilyOfTwoCompanies();
        graph.privacy(B, Map.of("SUPPLIER", "PRIVATE")).privacy(A, Map.of());

        impact.apply(command(UUID.randomUUID(), "F"));

        assertThat(json.readTree((String) outbox().getFirst().get("payload")).get("payload").get("affected").toString()).contains(id("U").toString(), B.toString());
    }

    /**
     * {@code name} solo lo ve la empresa dueña en las consultas; el evento de impacto va a todas las empresas afectadas, así que no lleva el nombre de ningún lote:
     * ni de la empresa del lote fallado (A, la que sería OWN), ni de la otra (B), privada o compartiendo todos los configurables.
     */
    @ParameterizedTest(name = "configuración: {0}")
    @ValueSource(strings = {"privado", "compartido"})
    void theImpactEventNeverCarriesTheNameOfAnyBatch(String privacy) {
        graph.named("F", "Nombre libre F de A").named("F2", "Nombre libre F2 de A").named("X", "Nombre libre X de B").named("U", "Nombre libre U de B");
        aFamilyOfTwoCompanies();
        if (privacy.equals("compartido")) {
            Map<String, String> everything = new java.util.LinkedHashMap<>();
            for (com.nodotextil.trazatex.traceability.domain.model.ConfigurableField field : com.nodotextil.trazatex.traceability.domain.model.ConfigurableField.values()) {
                everything.put(field.name(), "SHARED");
            }
            graph.privacy(A, everything).privacy(B, everything);
        }

        impact.apply(command(UUID.randomUUID(), "F"));

        String event = (String) outbox().getFirst().get("payload");
        assertThat(event).as("control: los afectados están").contains(id("F2").toString(), id("U").toString());
        assertThat(event).doesNotContain("Nombre libre", "\"name\"");
        assertThat(neo4j.query("MATCH (b:Batch {batchId: $id}) RETURN b.name AS n").bind(id("U").toString()).to("id").fetch().one().orElseThrow().get("n"))
                .as("control: el nombre sí está en el grafo").isEqualTo("Nombre libre U de B");
    }

    @Test
    void aFailureIsRecordedOnceEvenIfItsFailureConfirmedArrivesAgain() {
        aFamilyOfTwoCompanies();
        UUID failure = UUID.randomUUID();

        impact.apply(command(failure, "F"));
        impact.apply(command(failure, "F"));
        impact.apply(new AnalyzeFailureImpactCommand(UUID.randomUUID(), failure, id("F")));
        impact.apply(command(UUID.randomUUID(), "F"));

        assertThat(outbox()).as("una por falla: tres entregas de la misma y una de otra").hasSize(2);
        assertThat(outbox()).extracting(row -> row.get("aggregateId")).contains(failure.toString()).doesNotHaveDuplicates();
    }

    @Test
    void withNoAffectedBatchesNothingIsRecorded() {
        graph.batch("F", A).split("F", A, "F1").phase("F1", A, BatchPhase.PROCESSED, false, 2);

        impact.apply(command(UUID.randomUUID(), "F"));

        assertThat(outbox()).isEmpty();
    }

    // ---- incompleto: nada ----

    @Test
    void anIncompleteFamilyThrowsTheRetryableExceptionAndLeavesNothing() {
        graph.transform("T", A, List.of("GHOST"), "S"); // GHOST solo existe como entrada provisional: es antecesor de S

        assertThatThrownBy(() -> impact.apply(command(UUID.randomUUID(), "S"))).isInstanceOf(ImpactAnalysisIncompleteException.class)
                .satisfies(e -> assertThat(((ImpactAnalysisIncompleteException) e).missingNodes()).containsExactly(id("GHOST")));

        assertThat(outbox()).isEmpty();
    }

    @Test
    void anUnknownFailedBatchIsRetryableToo() {
        assertThatThrownBy(() -> impact.apply(command(UUID.randomUUID(), "NADIE"))).isInstanceOf(ImpactAnalysisIncompleteException.class);

        assertThat(outbox()).isEmpty();
    }

    @Test
    void onceTheMissingEventArrivesTheSameFailureConfirmedIsAnalyzedAndPublished() {
        graph.transform("T", A, List.of("GHOST"), "S", "S2");
        UUID failure = UUID.randomUUID();
        assertThatThrownBy(() -> impact.apply(command(failure, "S"))).isInstanceOf(ImpactAnalysisIncompleteException.class);

        graph.batch("GHOST", A).phase("GHOST", A, BatchPhase.PROCESSED, false, 2);
        impact.apply(command(failure, "S"));

        assertThat(json.readTree((String) outbox().getFirst().get("payload")).get("payload").get("affected")).extracting(n -> n.get("batchId").asString())
                .containsExactly(id("S2").toString());
    }

    // ---- la transacción: el procesado y el outbox van juntos ----

    private BaseDomainEvent failureConfirmed(UUID eventId, UUID failure, String batch) {
        return new BaseDomainEvent(eventId, "FailureConfirmed", failure.toString(), 1, Instant.now(), Map.of("failureId", failure, "batchId", id(batch)));
    }

    @Test
    void theProcessedEventAndTheOutboxRowAreCommittedTogether() {
        aFamilyOfTwoCompanies();
        UUID failure = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        handler.handle(failureConfirmed(eventId, failure, "F"), e -> impact.apply(new AnalyzeFailureImpactCommand(e.eventId(), failure, id("F"))));

        assertThat(count("ProcessedEvent")).isEqualTo(1);
        assertThat(outbox()).hasSize(1);
        // la misma entrega otra vez: el handler la reconoce y no hace nada
        handler.handle(failureConfirmed(eventId, failure, "F"), e -> impact.apply(new AnalyzeFailureImpactCommand(e.eventId(), failure, id("F"))));
        assertThat(count("ProcessedEvent")).isEqualTo(1);
        assertThat(outbox()).hasSize(1);
    }

    @Test
    void ifTheTransactionFailsAfterTheAnalysisNeitherTheOutboxRowNorTheProcessedEventRemain() {
        aFamilyOfTwoCompanies();
        UUID failure = UUID.randomUUID();

        assertThatThrownBy(() -> handler.handle(failureConfirmed(UUID.randomUUID(), failure, "F"), e -> {
            impact.apply(new AnalyzeFailureImpactCommand(e.eventId(), failure, id("F")));
            throw new IllegalStateException("se cae justo después");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(outbox()).isEmpty();
        assertThat(count("ProcessedEvent")).isZero();
    }

    @Test
    void anIncompleteFamilyInsideTheHandlerLeavesNoProcessedEventSoTheRetryAppliesItAgain() {
        graph.transform("T", A, List.of("GHOST"), "S");
        UUID failure = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        assertThatThrownBy(() -> handler.handle(failureConfirmed(eventId, failure, "S"), e -> impact.apply(new AnalyzeFailureImpactCommand(e.eventId(), failure, id("S")))))
                .isInstanceOf(ImpactAnalysisIncompleteException.class);

        assertThat(count("ProcessedEvent")).isZero();
        assertThat(outbox()).isEmpty();
    }

    // ---- costo ----

    @Test
    void theAnalysisOfTheFiveHundredNodeDiamondCostsTwoQueriesPerLevelAndNotOnePerNode() {
        DiamondGraph.load(neo4j, A, B);
        assertThat(DiamondGraph.nodeCount(neo4j)).isEqualTo(500L);
        UUID failure = UUID.randomUUID();
        UUID failed = DiamondGraph.batchId(5, 14);
        QueryCounting.reset();

        impact.apply(new AnalyzeFailureImpactCommand(UUID.randomUUID(), failure, failed));
        long queries = QueryCounting.count();

        // por dirección 2 por nivel × (10 niveles + 1 sonda) = 22; fijo: lote de partida, ¿ya registrada?, la fila del outbox
        long cap = 2L * 2 * 11 + 3;
        System.out.printf("CONSULTAS del análisis de impacto en el diamante de 500 nodos: %d (tope %d)%n", queries, cap);
        assertThat(queries).isLessThanOrEqualTo(cap);
        JsonNode affected = json.readTree((String) outbox().getFirst().get("payload")).get("payload").get("affected");
        assertThat(affected.size()).as("la familia del diamante es grande: cientos de lotes, en una sola publicación").isGreaterThan(100);
    }
}
