package com.nodotextil.trazatex.production.acceptance;

import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** US21 (iniciar una transferencia) y US22 (resolverla). Los avisos a otros servicios se comprueban en el evento del outbox. */
public class TransferSteps extends StepSupport {

    public TransferSteps(World world, ProductionApi api, Outbox outbox, JdbcClient jdbc, ObjectMapper json) {
        super(world, api, outbox, jdbc, json);
    }

    @SuppressWarnings("unchecked")
    private List<JsonNode> lots() {
        return (List<JsonNode>) world.notes.get("lots");
    }

    private List<String> lotIds() {
        return lots().stream().map(lot -> id(lot).toString()).toList();
    }

    // ---- US21 esc. 1 ----

    @Dado("^que existen lotes habilitados$")
    public void enabledBatchesExist() {
        world.notes.put("lots", List.of(registerLot(10), registerLot(20)));
    }

    @Dado("^selecciona una empresa destinataria de la misma cadena$")
    public void theOperatorSelectsADestinationCompanyOfTheSameChain() {
        world.pending = () -> api.post(world.operatorA, "/api/transfers", map("targetCompanyId", World.COMPANY_B.toString(), "batchIds", lotIds()));
    }

    @Y("^cambia los lotes a En transferencia$")
    public void theBatchesBecomeInTransfer() {
        String transfer = world.last.text("id");
        for (JsonNode lot : lots()) {
            assertThat(phaseOf(lot)).isEqualTo("IN_TRANSFER");
            List<Outbox.Event> changes = outbox.ofType("BatchPhaseChanged", id(lot).toString());
            assertThat(changes).hasSize(1);
            assertThat(changes.get(0).payload().path("previousPhase").asString()).isEqualTo("AVAILABLE");
            assertThat(changes.get(0).payload().path("newPhase").asString()).isEqualTo("IN_TRANSFER");
            assertThat(changes.get(0).payload().path("requestId").asString()).isEqualTo(transfer);
        }
    }

    @Y("^alerta a los administradores receptores\\.$")
    public void itAlertsTheReceivingAdministrators() {
        // el aviso lo envía Notification al consumir TransferInitiated: se verifica el evento, con la empresa receptora
        List<Outbox.Event> initiated = outbox.ofType("TransferInitiated", world.last.text("id"));
        assertThat(initiated).hasSize(1);
        assertThat(initiated.get(0).topic()).isEqualTo("production.transfer");
        JsonNode payload = initiated.get(0).payload();
        assertCatalogKeys(payload, "transferId", "batchIds", "fromCompanyId", "toCompanyId");
        assertThat(payload.path("toCompanyId").asString()).as("a quién hay que avisar").isEqualTo(World.COMPANY_B.toString());
        assertThat(payload.path("fromCompanyId").asString()).isEqualTo(World.COMPANY_A.toString());
        List<String> published = new ArrayList<>();
        payload.path("batchIds").forEach(batch -> published.add(batch.asString()));
        assertThat(published).containsExactlyInAnyOrderElementsOf(lotIds());
    }

    // ---- US21 esc. 2 ----

    @Dado("^que se desea transferir solamente parte de un lote$")
    public void onlyPartOfABatchShouldBeTransferred() {
        world.batch = registerLot(10);
        world.pending = () -> api.post(world.operatorA, "/api/transfers", map("targetCompanyId", World.COMPANY_B.toString(),
                "batchIds", List.of(id(world.batch).toString()), "quantitiesKg", map(id(world.batch).toString(), 4)));
    }

    @Entonces("^el sistema no lo permite$")
    public void theSystemDoesNotAllowIt() {
        assertThat(world.last.status()).as("transferencia parcial: %s", world.last.raw()).isEqualTo(400);
        assertThat(world.last.code()).isEqualTo("PARTIAL_TRANSFER_NOT_ALLOWED");
        assertThat(phaseOf(world.batch)).as("el lote no se movió").isEqualTo("AVAILABLE");
        assertThat(count("SELECT count(*) FROM transfers")).isZero();
        assertThat(outbox.ofType("TransferInitiated")).isEmpty();
        assertThat(outbox.ofType("BatchPhaseChanged")).isEmpty();
    }

    @Y("^requiere realizar previamente una división\\.$")
    public void itRequiresSplittingFirst() {
        assertThat(world.last.message()).isEqualTo("Solo se transfieren lotes completos: divida el lote antes");
        // y con la división hecha, el hijo de la cantidad deseada sí se transfiere
        Reply split = api.post(world.operatorA, "/api/batches/" + id(world.batch) + "/split",
                map("children", List.of(map("name", "Parte a transferir", "quantityKg", 4), map("name", "Resto", "quantityKg", 6))));
        assertThat(split.status()).isEqualTo(201);
        String child = split.body().path("children").get(0).path("id").asString();
        Reply transfer = api.post(world.operatorA, "/api/transfers", map("targetCompanyId", World.COMPANY_B.toString(),
                "batchIds", List.of(child), "quantitiesKg", map(child, 4)));
        assertThat(transfer.status()).as("transferir el hijo completo: %s", transfer.raw()).isEqualTo(201);
        assertThat(transfer.field("batchIds")).hasSize(1);
    }

    // ---- US22 ----

    @Dado("^que existe una transferencia Pendiente$")
    public void aPendingTransferExists() {
        world.notes.put("lots", List.of(registerLot(10), registerLot(20)));
        Reply transfer = api.post(world.operatorA, "/api/transfers", map("targetCompanyId", World.COMPANY_B.toString(), "batchIds", lotIds()));
        assertThat(transfer.status()).as("preparar la transferencia: %s", transfer.raw()).isEqualTo(201);
        assertThat(transfer.text("status")).isEqualTo("PENDING");
        world.transfer = transfer.body();
    }

    @Cuando("^el administrador la acepta$")
    public void theAdministratorAcceptsIt() {
        world.send(() -> api.post(world.adminB, "/api/transfers/" + id(world.transfer) + "/accept", null));
    }

    @Entonces("^el sistema registra fecha y hora de recepción$")
    public void theSystemRegistersTheReceptionDateAndTime() {
        assertThat(world.last.status()).as("aceptar: %s", world.last.raw()).isEqualTo(200);
        assertThat(world.last.text("status")).isEqualTo("ACCEPTED");
        assertWithinTheRequest(world.last.text("receptionTime"), "la fecha y hora de recepción");
        assertThat(world.last.text("resolvedBy")).isEqualTo(world.adminB.userId().toString());
    }

    @Y("^cambia la responsabilidad a la empresa receptora$")
    public void responsibilityMovesToTheReceivingCompany() {
        for (JsonNode lot : lots()) {
            Reply received = lotReply(world.operatorB, lot);
            assertThat(received.status()).as("la receptora ve el lote").isEqualTo(200);
            assertThat(received.text("companyId")).isEqualTo(World.COMPANY_B.toString());
            assertThat(lotReply(world.operatorA, lot).status()).as("el origen ya no lo ve").isEqualTo(404);
            List<Outbox.Event> available = outbox.ofType("BatchPhaseChanged", id(lot).toString()).stream()
                    .filter(event -> event.payload().path("newPhase").asString().equals("AVAILABLE")).toList();
            assertThat(available).hasSize(1);
            assertThat(available.get(0).payload().path("companyId").asString()).as("la empresa dueña tras el cambio").isEqualTo(World.COMPANY_B.toString());
        }
        List<Outbox.Event> accepted = outbox.ofType("TransferAccepted", id(world.transfer).toString());
        assertThat(accepted).hasSize(1);
        assertCatalogKeys(accepted.get(0).payload(), "transferId", "batchIds", "fromCompanyId", "toCompanyId", "acceptedAt");
        assertThat(accepted.get(0).payload().path("fromCompanyId").asString()).isEqualTo(World.COMPANY_A.toString());
        assertThat(accepted.get(0).payload().path("toCompanyId").asString()).isEqualTo(World.COMPANY_B.toString());
    }

    @Y("^establece los lotes como Disponible$")
    public void itSetsTheBatchesAsAvailable() {
        for (JsonNode lot : lots()) {
            assertThat(lot(world.operatorB, lot).path("phase").asString()).isEqualTo("AVAILABLE");
        }
    }

    @Y("^conserva sus Traceability ID y QR\\.$")
    public void itKeepsTheirTraceabilityIdAndQr() {
        for (JsonNode before : lots()) {
            JsonNode after = lot(world.operatorB, before);
            assertThat(after.path("traceabilityId").asString()).isEqualTo(before.path("traceabilityId").asString());
            assertThat(after.path("qrCode").asString()).isEqualTo(before.path("qrCode").asString());
            assertThat(after.path("id").asString()).isEqualTo(before.path("id").asString());
        }
    }

    @Dado("^registra el motivo del rechazo$")
    public void theAdministratorRecordsTheRejectionReason() {
        world.body = map("reason", "Material con humedad");
        world.pending = () -> api.post(world.adminB, "/api/transfers/" + id(world.transfer) + "/reject", world.body);
    }

    @Entonces("^el sistema registra fecha y hora$")
    public void theSystemRegistersTheDateAndTime() {
        assertThat(world.last.status()).as("rechazar: %s", world.last.raw()).isEqualTo(200);
        assertThat(world.last.text("status")).isEqualTo("REJECTED");
        assertWithinTheRequest(world.last.text("resolvedAt"), "la fecha y hora del rechazo");
        assertThat(world.last.text("rejectionReason")).isEqualTo("Material con humedad");
        assertThat(world.last.field("receptionTime").isNull()).as("un rechazo no es una recepción").isTrue();
    }

    @Y("^devuelve los lotes a la empresa de origen como Disponible$")
    public void itReturnsTheBatchesToTheOriginCompanyAsAvailable() {
        for (JsonNode lot : lots()) {
            JsonNode back = lot(world.operatorA, lot);
            assertThat(back.path("phase").asString()).isEqualTo("AVAILABLE");
            assertThat(back.path("companyId").asString()).isEqualTo(World.COMPANY_A.toString());
            assertThat(lotReply(world.operatorB, lot).status()).as("la destinataria nunca llegó a tenerlo").isEqualTo(404);
        }
    }

    @Y("^alerta a sus administradores\\.$")
    public void itAlertsTheirAdministrators() {
        List<Outbox.Event> rejected = outbox.ofType("TransferRejected", id(world.transfer).toString());
        assertThat(rejected).hasSize(1);
        assertThat(rejected.get(0).topic()).isEqualTo("production.transfer");
        JsonNode payload = rejected.get(0).payload();
        assertCatalogKeys(payload, "transferId", "batchIds", "fromCompanyId", "toCompanyId");
        assertThat(payload.path("fromCompanyId").asString()).as("a los administradores de la empresa de origen").isEqualTo(World.COMPANY_A.toString());
        assertThat(payload.path("toCompanyId").asString()).isEqualTo(World.COMPANY_B.toString());
    }
}
