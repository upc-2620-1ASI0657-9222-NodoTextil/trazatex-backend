package com.nodotextil.trazatex.traceability.acceptance;

import com.nodotextil.trazatex.traceability.integration.EventMessages;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US33 — Detectar posible falla derivada (RF-058 a RF-060). Los pasos hacen de Production (publican los eventos de los lotes) y de Quality (publican
 * {@code FailureConfirmed} en {@code quality.failure}); Traceability analiza la familia y su resultado sale <b>solo</b> por el evento
 * {@code PossibleDerivedFailureDetected} en {@code traceability.impact}, que los pasos leen del tópico (Kafka real, el relay del outbox de verdad).
 *
 * <p><b>Escenario 3 («cambia su calidad a Posible falla derivada y lo bloquea preventivamente»)</b>: ese efecto lo ejecutan <b>Quality</b> (calidad
 * {@code POTENTIAL_DERIVED_FAILURE}) y <b>Production</b> (el comando interno {@code HOLD}, con {@code requestId = failureId}) en el siguiente sprint; Traceability no cambia la
 * calidad ni bloquea nada (docs/domain/traceability.md). El texto del escenario no se reescribe: los pasos <b>verifican el evento que lo dispara</b> —que el lote figura
 * en {@code PossibleDerivedFailureDetected}, con la falla (el {@code requestId} del {@code HOLD}) y su empresa— y que Traceability no tocó el lote. Regla general en
 * docs/conventions.md.
 */
public class ImpactSteps {

    private final World world;
    private final Platform platform;

    /** La falla del escenario y el sobre de {@code PossibleDerivedFailureDetected} que Traceability publicó. */
    private UUID failure;
    private UUID failedBatch;
    private JsonNode event;

    public ImpactSteps(World world, Platform platform) {
        this.world = world;
        this.platform = platform;
    }

    // ---- el grafo, por eventos ----

    private UUID id(String name) {
        return world.ids.computeIfAbsent(name, key -> UUID.randomUUID());
    }

    private void register(String name) {
        platform.publishAndAwait(List.of(EventMessages.batchRegistered(UUID.randomUUID(), id(name), "TT-" + name, "QR-" + name, world.companyA, "Algodón", 100, "Piura",
                "Textiles del Norte", "Fibra limpia", World.T0)));
    }

    private void split(String parent, String... children) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String child : children) {
            list.add(EventMessages.splitChild(id(child), "TT-" + child, "QR-" + child, "Algodón", 20, "Piura", "Textiles del Norte", "Fibra limpia", "NOT_REVIEWED",
                    World.T0.plusSeconds(60)));
        }
        platform.publishAndAwait(List.of(EventMessages.batchSplit(UUID.randomUUID(), id(parent), world.companyA, list)));
    }

    private void transform(String name, List<String> inputs, String output) {
        platform.publishAndAwait(List.of(EventMessages.transformationCompleted(UUID.randomUUID(), id(name), "DYEING", world.companyA, "Teñidora", "Teñido",
                inputs.stream().map(this::id).toList(), id(output), "TT-" + output, "QR-" + output, "Tela teñida", 80, false, 5, "Orillo", 5, World.T0, World.T0.plusSeconds(3600))));
    }

    /** El lote pasa a una fase (los lotes históricos son los que ya no operan). */
    private void phase(String name, String newPhase) {
        platform.publishAndAwait(List.of(EventMessages.phaseChanged(UUID.randomUUID(), id(name), world.companyA, "AVAILABLE", newPhase, false, 2)));
    }

    /** Quality confirma la falla del lote: se deja <i>preparado</i> y lo publica el {@code Cuando}. */
    private void failureConfirmedFor(String batch) {
        failure = UUID.randomUUID();
        failedBatch = id(batch);
        world.pending.add(EventMessages.of("quality.failure", failure.toString(), UUID.randomUUID(), "FailureConfirmed", EventMessages.map("failureId", failure, "batchId", failedBatch,
                "controlId", UUID.randomUUID(), "companyId", world.companyA, "cause", "Hilado irregular", "affectedQuantityKg", 12.5, "confirmedAt", Instant.parse("2026-10-21T10:00:00Z"))));
    }

    /** Traceability analiza la familia al consumir el evento y lo que encuentra sale por {@code traceability.impact}. */
    private void theAnalysisRuns() {
        platform.publishAndAwait(world.pending);
        event = platform.awaitImpact(failure);
    }

    // ---- lo que dice el evento ----

    /** Los lotes afectados del evento como {@code lote → antecesor común}, por nombre. */
    private Map<String, String> affected() {
        Map<UUID, String> names = new TreeMap<>();
        world.ids.forEach((name, uuid) -> names.put(uuid, name));
        Map<String, String> affected = new TreeMap<>();
        for (JsonNode node : event.get("payload").get("affected")) {
            affected.put(names.get(UUID.fromString(node.get("batchId").asString())), names.get(UUID.fromString(node.get("commonAncestorBatchId").asString())));
        }
        return affected;
    }

    private void assertTheEventIsOfThisFailure() {
        assertThat(event.get("type").asString()).isEqualTo("PossibleDerivedFailureDetected");
        assertThat(event.get("payload").get("failureId").asString()).isEqualTo(failure.toString());
        assertThat(event.get("payload").get("sourceBatchId").asString()).isEqualTo(failedBatch.toString());
        assertThat(event.get("aggregateId").asString()).as("la clave del mensaje es la falla").isEqualTo(failure.toString());
    }

    // ==== Escenario 1: análisis de familia relacionada ====

    @Dado("^que una Falla ha sido Confirmada$")
    public void aFailureHasBeenConfirmed() {
        // G se dividió en S (el lote fallado, descartado) y T (su hermano, disponible); G ya no opera (dividido)
        register("G");
        split("G", "S", "T");
        phase("G", "SPLIT");
        phase("S", "DISCARDED");
        failureConfirmedFor("S");
    }

    @Cuando("^el sistema analiza la genealogía hacia atrás$")
    public void theSystemAnalyzesTheGenealogyBackwards() {
        theAnalysisRuns();
    }

    @Entonces("^utiliza los lotes históricos para localizar bifurcaciones y otros lotes relacionados de la misma familia\\.$")
    public void itUsesTheHistoricBatchesToFindForksAndOtherRelatedBatchesOfTheSameFamily() {
        assertTheEventIsOfThisFailure();
        assertThat(affected()).as("T no desciende del lote fallado: solo se llega a él pasando por G, un lote histórico, y G es su antecesor común").containsExactly(Map.entry("T", "G"));
    }

    // ==== Escenario 2: descendientes relacionados ====

    @Dado("^que existen divisiones dentro de las relaciones encontradas$")
    public void thereAreDivisionsWithinTheRelationsFound() {
        // T (hermano del fallado) se dividió en T1, T2 y T3; T3 se descartó y T ya no opera (dividido)
        register("G");
        split("G", "S", "T");
        split("T", "T1", "T2", "T3");
        phase("G", "SPLIT");
        phase("S", "DISCARDED");
        phase("T", "SPLIT");
        phase("T3", "DISCARDED");
        failureConfirmedFor("S");
    }

    @Cuando("^analiza sus descendientes$")
    public void itAnalyzesTheirDescendants() {
        theAnalysisRuns();
    }

    @Entonces("^revisa los lotes relacionados que continúan Disponibles\\.$")
    public void itReviewsTheRelatedBatchesThatAreStillAvailable() {
        assertThat(affected()).as("T1 y T2 siguen disponibles; T (dividido) y T3 (descartado) no").containsExactly(Map.entry("T1", "G"), Map.entry("T2", "G"));
    }

    // ==== Escenario 3: posible falla derivada (el efecto lo ejecutan Quality y Production, ver el Javadoc de la clase) ====

    @Dado("^que un lote relacionado continúa Disponible$")
    public void aRelatedBatchIsStillAvailable() {
        register("G");
        split("G", "S", "T");
        phase("G", "SPLIT");
        phase("S", "DISCARDED");
        failureConfirmedFor("S");
    }

    @Cuando("^el sistema determina su posible relación con la falla$")
    public void theSystemDeterminesItsPossibleRelationWithTheFailure() {
        theAnalysisRuns();
    }

    /**
     * «Cambia su calidad a Posible falla derivada»: lo hace Quality al consumir el evento (siguiente sprint). Aquí se verifica el evento que lo dispara: el lote está en
     * {@code PossibleDerivedFailureDetected} con su empresa, que es lo que Quality necesita para marcarlo.
     */
    @Entonces("^cambia su calidad a Posible falla derivada$")
    public void itChangesItsQualityToPotentialDerivedFailure() {
        assertTheEventIsOfThisFailure();
        assertThat(affected()).containsOnlyKeys("T");
        JsonNode affected = event.get("payload").get("affected").get(0);
        assertThat(affected.get("batchId").asString()).isEqualTo(id("T").toString());
        assertThat(affected.get("companyId").asString()).as("la empresa del lote: a ella notifica Notification y a su Production pide Quality el bloqueo").isEqualTo(world.companyA.toString());
    }

    /**
     * «Lo bloquea preventivamente»: lo hace Production con el comando interno {@code HOLD} que le envía Quality (siguiente sprint). Aquí se verifica que el evento trae lo que ese
     * comando necesita ({@code requestId = failureId}, la clave del mensaje) y que Traceability <b>no</b> cambió la calidad ni bloqueó el lote: eso no es suyo.
     */
    @Y("^lo bloquea preventivamente\\.$")
    public void itBlocksItPreventively() {
        assertThat(event.get("aggregateId").asString()).as("el requestId del HOLD es la falla").isEqualTo(failure.toString());
        Map<String, Object> batch = platform.batch(id("T"));
        assertThat(batch).as("Traceability no marca la calidad ni retiene el lote").containsEntry("qualityStatus", "NOT_REVIEWED").containsEntry("qualityHold", false);
        assertThat(batch).containsEntry("phase", "AVAILABLE");
    }

    // ==== Escenario 4: lote histórico ====

    @Dado("^que un lote relacionado se encuentra Procesado, Dividido o Descartado$")
    public void aRelatedBatchIsProcessedSplitOrDiscarded() {
        // G → S (fallado), P (procesado: entró a la transformación TX y salió O), Sp (dividido: sus hijo C), D (descartado) y A (disponible)
        register("G");
        split("G", "S", "P", "Sp", "D", "A");
        split("Sp", "C");
        transform("TX", List.of("P"), "O");
        phase("G", "SPLIT");
        phase("S", "DISCARDED");
        phase("P", "PROCESSED");
        phase("Sp", "SPLIT");
        phase("D", "DISCARDED");
        failureConfirmedFor("S");
    }

    @Cuando("^analiza la genealogía$")
    public void itAnalyzesTheGenealogy() {
        theAnalysisRuns();
    }

    @Entonces("^lo utiliza únicamente para reconstruir las relaciones$")
    public void itUsesItOnlyToRebuildTheRelationships() {
        Map<String, String> affected = affected();
        assertThat(affected).as("C y O solo se alcanzan pasando por Sp (dividido) y P (procesado): se usaron para reconstruir; A es otro hijo de G")
                .containsExactly(Map.entry("A", "G"), Map.entry("C", "G"), Map.entry("O", "G"));
        Set<String> historic = new TreeSet<>(Set.of("G", "P", "Sp", "D", "S"));
        assertThat(affected.keySet()).as("ni uno solo de los históricos se devuelve como afectado").doesNotContainAnyElementsOf(historic);
    }

    @Y("^no lo devuelve a operación\\.$")
    public void itDoesNotReturnItToOperation() {
        assertThat(platform.batch(id("P"))).containsEntry("phase", "PROCESSED");
        assertThat(platform.batch(id("Sp"))).containsEntry("phase", "SPLIT");
        assertThat(platform.batch(id("D"))).containsEntry("phase", "DISCARDED");
        assertThat(platform.batch(id("G"))).containsEntry("phase", "SPLIT");
        assertThat(platform.count("MATCH (b:Batch) WHERE b.phase IN ['PROCESSED', 'SPLIT', 'DISCARDED'] RETURN count(b) AS n", Map.of()))
                .as("G, P, Sp, D y S siguen históricos: ninguno volvió a AVAILABLE").isEqualTo(5L);
    }
}
