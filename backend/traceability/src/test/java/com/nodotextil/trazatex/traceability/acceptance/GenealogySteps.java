package com.nodotextil.trazatex.traceability.acceptance;

import com.nodotextil.trazatex.traceability.integration.EventMessages;
import com.nodotextil.trazatex.traceability.integration.EventMessages.Message;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US29 — Generar genealogía (RF-053). Los pasos hacen de Production: publican sus eventos en los tópicos de Kafka, con el sobre y el payload
 * del catálogo, y esperan a que traceability los procese; después miran el grafo con Cypher. Los {@code Dado} publican el estado previo
 * (el lote ya registrado) y dejan <i>preparado</i> lo que ocurre en el {@code Cuando}.
 */
public class GenealogySteps {

    private final World world;
    private final Platform platform;

    public GenealogySteps(World world, Platform platform) {
        this.world = world;
        this.platform = platform;
    }

    private static String code(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
    }

    private UUID registerBatch(String name, double kg) {
        UUID id = UUID.randomUUID();
        String traceabilityId = code("TT");
        String qr = code("QR");
        if (world.batch == null) {
            world.batch = id;
            world.batchTraceabilityId = traceabilityId;
            world.batchQrCode = qr;
        }
        platform.publishAndAwait(List.of(EventMessages.batchRegistered(UUID.randomUUID(), id, traceabilityId, qr, world.companyA, "Algodón", kg,
                "Piura", "Textiles del Norte", "Fibra limpia", World.T0)));
        return id;
    }

    // ---- Escenario 1: relación por división ----

    @Dado("^que un lote ha sido dividido$")
    public void aBatchHasBeenSplit() {
        UUID parent = registerBatch("padre", 100);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        world.children.addAll(List.of(first, second));
        world.pending.add(EventMessages.batchSplit(UUID.randomUUID(), parent, world.companyA, List.of(
                EventMessages.splitChild(first, code("TT"), code("QR"), "Algodón", 40, "Piura", "Textiles del Norte", "Fibra limpia", "NOT_REVIEWED",
                        World.T0.plusSeconds(60)),
                EventMessages.splitChild(second, code("TT"), code("QR"), "Algodón", 60, "Piura", "Textiles del Norte", "Fibra limpia", "NOT_REVIEWED",
                        World.T0.plusSeconds(60)))));
        world.pending.add(EventMessages.phaseChanged(UUID.randomUUID(), parent, world.companyA, "AVAILABLE", "SPLIT", false, 1));
    }

    @Cuando("^se generan sus hijos$")
    public void theChildrenAreGenerated() {
        platform.publishAndAwait(world.pending);
    }

    @Entonces("^el sistema registra la relación padre-hijo\\.$")
    public void theParentChildRelationshipIsRecorded() {
        for (UUID child : world.children) {
            assertThat(platform.count("MATCH (:Batch {batchId: $parent})-[r:SPLIT_INTO]->(:Batch {batchId: $child}) RETURN count(r) AS n",
                    Map.of("parent", world.batch.toString(), "child", child.toString()))).as("relación SPLIT_INTO del padre al hijo %s", child).isEqualTo(1);
            Map<String, Object> node = platform.batch(child);
            assertThat(node).as("el hijo es un lote completo, no provisional").containsEntry("provisional", false).containsKeys("traceabilityId", "qrCode");
            assertThat(node.get("traceabilityId")).as("el hijo tiene su propio Traceability ID").isNotEqualTo(world.batchTraceabilityId);
        }
        assertThat(platform.count("MATCH (:Batch {batchId: $parent})-[r:SPLIT_INTO]->(:Batch) RETURN count(r) AS n", Map.of("parent", world.batch.toString())))
                .as("una relación por cada hijo y ninguna más").isEqualTo(world.children.size());
        assertThat(platform.batch(world.batch)).as("el padre ya figura como dividido").containsEntry("phase", "SPLIT");
    }

    // ---- Escenario 2: relación por transformación ----

    @Dado("^que una transformación genera lotes de salida$")
    public void aTransformationProducesOutputBatches() {
        world.transformation = UUID.randomUUID();
        world.inputs.add(registerBatch("entrada 1", 60));
        world.inputs.add(registerBatch("entrada 2", 40));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        world.outputs.addAll(List.of(first, second));
        world.pending.add(EventMessages.transformationCompleted(UUID.randomUUID(), world.transformation, "DYEING", world.companyA, "Cardadora", "Cardado",
                world.inputs, List.of(EventMessages.transformationOutput(first, code("TT"), code("QR"), "Tela teñida", 50, false),
                        EventMessages.transformationOutput(second, code("TT"), code("QR"), "Tela teñida", 40, true)), 5, "Orillo", 5,
                World.T0, World.T0.plusSeconds(3600)));
        for (UUID input : world.inputs) {
            world.pending.add(EventMessages.phaseChanged(UUID.randomUUID(), input, world.companyA, "IN_TRANSFORMATION", "PROCESSED", false, 2));
        }
    }

    @Cuando("^finaliza correctamente$")
    public void itCompletesSuccessfully() {
        platform.publishAndAwait(world.pending);
    }

    @Entonces("^relaciona todas sus entradas con sus salidas\\.$")
    public void everyInputIsRelatedToEveryOutput() {
        for (UUID input : world.inputs) {
            for (UUID output : world.outputs) {
                assertThat(platform.count("MATCH (:Batch {batchId: $in})-[:INPUT_OF]->(:Transformation {transformationId: $t})-[:PRODUCED]->(:Batch {batchId: $out}) RETURN count(*) AS n",
                        Map.of("in", input.toString(), "t", world.transformation.toString(), "out", output.toString())))
                        .as("entrada %s → transformación → salida %s", input, output).isEqualTo(1);
            }
        }
        Map<String, Object> transformation = Map.of("t", world.transformation.toString());
        assertThat(platform.count("MATCH (:Batch)-[r:INPUT_OF]->(:Transformation {transformationId: $t}) RETURN count(r) AS n", transformation))
                .as("todas las entradas y solo ellas").isEqualTo(world.inputs.size());
        assertThat(platform.count("MATCH (:Transformation {transformationId: $t})-[r:PRODUCED]->(:Batch) RETURN count(r) AS n", transformation))
                .as("todas las salidas y solo ellas").isEqualTo(world.outputs.size());
        for (UUID output : world.outputs) {
            assertThat(platform.batch(output)).as("la salida es un lote completo, no provisional").containsEntry("provisional", false).containsKey("traceabilityId");
        }
    }

    // ---- Escenario 3: transferencia ----

    @Dado("^que un lote es transferido entre empresas$")
    public void aBatchIsTransferredBetweenCompanies() {
        UUID batch = registerBatch("transferido", 100);
        world.transfer = UUID.randomUUID();
        world.pending.add(EventMessages.transferAccepted(UUID.randomUUID(), world.transfer, List.of(batch), world.companyA, world.companyB, World.T0.plusSeconds(120)));
        world.pending.add(EventMessages.phaseChanged(UUID.randomUUID(), batch, world.companyB, "IN_TRANSFER", "AVAILABLE", false, 2));
    }

    @Cuando("^se resuelve la transferencia$")
    public void theTransferIsResolved() {
        platform.publishAndAwait(world.pending);
    }

    @Entonces("^el sistema conserva la transferencia dentro del historial del mismo lote$")
    public void theTransferIsKeptInTheHistoryOfTheSameBatch() {
        assertThat(platform.count("MATCH (b:Batch {batchId: $id})-[r:TRANSFERRED_VIA {transferId: $transfer}]->(c:Company {companyId: $to}) WHERE r.at IS NOT NULL RETURN count(r) AS n",
                Map.of("id", world.batch.toString(), "transfer", world.transfer.toString(), "to", world.companyB.toString())))
                .as("TRANSFERRED_VIA con la transferencia y su fecha, en el mismo lote").isEqualTo(1);
        Map<String, Object> node = platform.batch(world.batch);
        assertThat(node).as("el lote conserva su identidad").containsEntry("traceabilityId", world.batchTraceabilityId).containsEntry("qrCode", world.batchQrCode);
        assertThat(node).as("y ahora es de la empresa receptora").containsEntry("companyId", world.companyB.toString());
    }

    @Y("^no crea un nuevo lote ni una relación genealógica padre-hijo\\.$")
    public void noNewBatchNorParentChildRelationshipIsCreated() {
        assertThat(platform.count("MATCH (b:Batch) RETURN count(b) AS n", Map.of())).as("sigue habiendo un solo lote").isEqualTo(1);
        assertThat(platform.count("MATCH ()-[r:SPLIT_INTO|INPUT_OF|PRODUCED]->() RETURN count(r) AS n", Map.of())).as("ninguna relación genealógica").isZero();
        assertThat(platform.count("MATCH (t:Transformation) RETURN count(t) AS n", Map.of())).as("ninguna transformación").isZero();
    }
}
