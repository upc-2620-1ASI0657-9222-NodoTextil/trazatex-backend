package com.nodotextil.trazatex.production.acceptance;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** US16 (iniciar), US17 (composición), US18 (finalizar), US19 (desperdicio) y US20 (producto final). */
public class TransformationSteps extends StepSupport {

    public TransformationSteps(World world, ProductionApi api, Outbox outbox, JdbcClient jdbc, ObjectMapper json) {
        super(world, api, outbox, jdbc, json);
    }

    @SuppressWarnings("unchecked")
    private List<JsonNode> lots() {
        return (List<JsonNode>) world.notes.get("lots");
    }

    private void prepareCompletion(Map<String, Object> body) {
        world.body = body;
        world.pending = () -> completeTransformation(world.transformation, world.body);
    }

    private static List<Map<String, Object>> outputs(Object... quantities) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object quantity : quantities) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("name", "Tela");
            output.put("materialType", "Tela");
            output.put("quantityKg", quantity);
            list.add(output);
        }
        return list;
    }

    // ---- US16 esc. 1 ----

    @Dado("^que existen uno o más lotes habilitados$")
    public void oneOrMoreEnabledBatchesExist() {
        world.notes.put("lots", List.of(registerLot(10), registerLot(20)));
    }

    @Dado("^el operario selecciona una maquinaria Activa y un tipo de transformación$")
    public void theOperatorSelectsAnActiveMachineAndATransformationType() {
        world.machine = registerMachine();
        world.pending = () -> api.post(world.operatorA, "/api/transformations", map("type", "SPINNING", "machineId", id(world.machine).toString(),
                "batchIds", lots().stream().map(lot -> id(lot).toString()).toList()));
    }

    @Y("^cambia los lotes a En transformación\\.$")
    public void theBatchesBecomeInTransformation() {
        String transformation = world.last.text("id");
        for (JsonNode lot : lots()) {
            assertThat(phaseOf(lot)).isEqualTo("IN_TRANSFORMATION");
            List<Outbox.Event> changes = outbox.ofType("BatchPhaseChanged", id(lot).toString());
            assertThat(changes).hasSize(1);
            assertThat(changes.get(0).payload().path("previousPhase").asString()).isEqualTo("AVAILABLE");
            assertThat(changes.get(0).payload().path("newPhase").asString()).isEqualTo("IN_TRANSFORMATION");
            assertThat(changes.get(0).payload().path("requestId").asString()).isEqualTo(transformation);
        }
    }

    // ---- US16 esc. 2 ----

    @Dado("^que uno de los lotes seleccionados no está habilitado$")
    public void oneOfTheSelectedBatchesIsNotEnabled() {
        JsonNode enabled = registerLot(10);
        JsonNode retained = registerLot(20);
        holdForQuality(retained);
        world.notes.put("lots", List.of(enabled, retained));
        world.machine = registerMachine();
        world.pending = () -> api.post(world.operatorA, "/api/transformations", map("type", "SPINNING", "machineId", id(world.machine).toString(),
                "batchIds", lots().stream().map(lot -> id(lot).toString()).toList()));
    }

    @Entonces("^el sistema no permite iniciarla\\.$")
    public void theSystemDoesNotAllowStartingIt() {
        assertThat(world.last.status()).as("iniciar con un lote retenido: %s", world.last.raw()).isEqualTo(409);
        assertThat(world.last.code()).isEqualTo("BATCH_ON_QUALITY_HOLD");
        JsonNode enabled = lots().get(0);
        assertThat(phaseOf(enabled)).as("el lote habilitado tampoco se movió").isEqualTo("AVAILABLE");
        assertThat(api.get(world.operatorA, "/api/transformations").body().path("items")).isEmpty();
        assertThat(outbox.ofType("BatchPhaseChanged", id(enabled).toString())).isEmpty();
    }

    // ---- US17 ----

    @Dado("^que la transformación utiliza un solo lote$")
    public void theTransformationUsesASingleBatch() {
        JsonNode input = registerLot(100);
        world.notes.put("input", input);
        startTransformation("SPINNING", registerMachine(), input);
        prepareCompletion(map("outputs", outputs(45, 45)));
    }

    @Entonces("^el sistema conserva la composición del lote de entrada\\.$")
    public void theSystemKeepsTheInputComposition() {
        assertThat(world.last.status()).as("finalizar: %s", world.last.raw()).isEqualTo(200);
        JsonNode input = lot(world.operatorA, (JsonNode) world.notes.get("input"));
        assertThat(world.last.field("outputs")).hasSize(2);
        for (JsonNode output : world.last.field("outputs")) {
            assertThat(composition(output)).isEqualTo(composition(input));
        }
        assertThat(composition(input)).containsExactly("Algodón=70.00", "Poliéster=30.00");
        assertThat(world.last.field("transformation").path("mix").asBoolean()).isFalse();
    }

    @Dado("^que la transformación utiliza varios lotes$")
    public void theTransformationUsesSeveralBatches() {
        Map<String, Object> cotton = lotBody(100);
        cotton.put("composition", List.of(component("Algodón", 100)));
        Map<String, Object> blend = lotBody(300);
        blend.put("composition", List.of(component("Algodón", 50), component("Poliéster", 50)));
        startTransformation("SPINNING", registerMachine(), registerLot(cotton), registerLot(blend));
        prepareCompletion(map("outputs", outputs(360)));
    }

    @Entonces("^calcula la composición resultante mediante promedio ponderado por kg$")
    public void itComputesTheWeightedAverageByKg() {
        assertThat(world.last.status()).as("finalizar: %s", world.last.raw()).isEqualTo(200);
        // (100 kg × 100 % + 300 kg × 50 %) / 400 kg = 62,5 % de algodón y 37,5 % de poliéster
        assertThat(composition(world.last.field("outputs").get(0))).containsExactly("Algodón=62.50", "Poliéster=37.50");
    }

    @Y("^considera la operación como una mezcla\\.$")
    public void itConsidersTheOperationAMix() {
        assertThat(world.last.field("transformation").path("mix").asBoolean()).isTrue();
        Reply stored = api.get(world.operatorA, "/api/transformations/" + id(world.transformation));
        assertThat(stored.field("mix").asBoolean()).isTrue();
        assertThat(stored.field("inputs")).hasSize(2);
    }

    private static List<String> composition(JsonNode batch) {
        List<String> parts = new ArrayList<>();
        batch.path("composition").forEach(c -> parts.add(c.path("name").asString() + "=" + c.path("percentage").decimalValue().setScale(2).toPlainString()));
        return parts;
    }

    // ---- US18 ----

    @Dado("^que existe una transformación iniciada$")
    public void aTransformationHasStarted() {
        JsonNode input = registerLot(100);
        world.notes.put("input", input);
        startTransformation("DYEING", registerMachine(), input);
    }

    @Dado("^el operario registra uno o más lotes de salida y, cuando corresponda, la cantidad y motivo del desperdicio$")
    public void theOperatorRegistersOutputsAndWaste() {
        prepareCompletion(map("outputs", outputs(85), "wasteKg", 5, "wasteReason", "Orillo y recortes"));
    }

    @Entonces("^el sistema calcula la merma como Entrada total − Salida total − Desperdicio$")
    public void theSystemComputesTheShrinkage() {
        assertThat(world.last.status()).as("finalizar: %s", world.last.raw()).isEqualTo(200);
        JsonNode result = world.last.field("transformation");
        BigDecimal input = result.path("totalInputKg").decimalValue();
        BigDecimal output = result.path("totalOutputKg").decimalValue();
        BigDecimal waste = result.path("wasteKg").decimalValue();
        assertThat(input).isEqualByComparingTo("100");
        assertThat(output).isEqualByComparingTo("85");
        assertThat(waste).isEqualByComparingTo("5");
        assertThat(result.path("shrinkageKg").decimalValue()).as("la merma se calcula, no se ingresa").isEqualByComparingTo(input.subtract(output).subtract(waste));
        assertThat(result.path("shrinkageKg").decimalValue()).isEqualByComparingTo("10");
    }

    @Y("^finaliza cuando la merma es mayor o igual a cero$")
    public void itFinishesWhenTheShrinkageIsZeroOrMore() {
        assertThat(world.last.field("transformation").path("status").asString()).isEqualTo("COMPLETED");
        assertThat(world.last.field("transformation").path("endTime").isNull()).isFalse();
        // el límite: merma exactamente cero (salida + desperdicio = entrada) también finaliza
        JsonNode input = registerLot(100);
        JsonNode edge = startTransformation("DYEING", registerMachine(), input);
        Reply zero = completeTransformation(edge, map("outputs", outputs(90), "wasteKg", 10, "wasteReason", "Recortes"));
        assertThat(zero.status()).as("merma cero: %s", zero.raw()).isEqualTo(200);
        assertThat(zero.field("transformation").path("shrinkageKg").decimalValue()).isEqualByComparingTo("0");
        assertThat(zero.field("transformation").path("status").asString()).isEqualTo("COMPLETED");
    }

    @Y("^establece las entradas como Procesado y las salidas como Disponible\\.$")
    public void itSetsTheInputsAsProcessedAndTheOutputsAsAvailable() {
        JsonNode input = (JsonNode) world.notes.get("input");
        assertThat(phaseOf(input)).isEqualTo("PROCESSED");
        List<Outbox.Event> changes = outbox.ofType("BatchPhaseChanged", id(input).toString());
        assertThat(changes.get(changes.size() - 1).payload().path("newPhase").asString()).isEqualTo("PROCESSED");
        JsonNode output = world.last.field("outputs").get(0);
        JsonNode stored = lot(world.operatorA, output);
        assertThat(stored.path("phase").asString()).isEqualTo("AVAILABLE");
        assertThat(stored.path("qualityStatus").asString()).isEqualTo("NOT_REVIEWED");
        // y los demás servicios reciben el balance completo
        List<Outbox.Event> completed = outbox.ofType("TransformationCompleted", world.last.field("transformation").path("id").asString());
        assertThat(completed).hasSize(1);
        assertCatalogKeys(completed.get(0).payload(), "transformationId", "type", "companyId", "machineId", "machineName", "machineType",
                "inputs", "outputs", "wasteKg", "wasteReason", "shrinkageKg", "startedAt", "completedAt");
        assertThat(completed.get(0).payload().path("shrinkageKg").decimalValue()).isEqualByComparingTo("10");
        assertThat(completed.get(0).payload().path("inputs").get(0).path("batchId").asString()).isEqualTo(input.path("id").asString());
        assertThat(completed.get(0).payload().path("outputs").get(0).path("batchId").asString()).isEqualTo(output.path("id").asString());
        // name de la salida (agregado en V2): el que envió el operario y el que guarda el lote de salida
        @SuppressWarnings("unchecked")
        Object requestedName = ((List<Map<String, Object>>) world.body.get("outputs")).get(0).get("name");
        assertThat(completed.get(0).payload().path("outputs").get(0).path("name").asString()).isEqualTo(requestedName).isEqualTo(stored.path("name").asString());
    }

    @Dado("^que la salida total más el desperdicio supera la entrada total$")
    public void theOutputPlusWasteExceedsTheInput() {
        JsonNode input = registerLot(100);
        world.notes.put("input", input);
        startTransformation("DYEING", registerMachine(), input);
        prepareCompletion(map("outputs", outputs(95), "wasteKg", 10, "wasteReason", "Orillo"));
    }

    @Entonces("^el sistema no permite finalizarla$")
    public void theSystemDoesNotAllowFinishingIt() {
        assertThat(world.last.status()).as("finalizar con balance inválido: %s", world.last.raw()).isEqualTo(400);
        assertThat(api.get(world.operatorA, "/api/transformations/" + id(world.transformation)).text("status")).isEqualTo("IN_PROGRESS");
        assertThat(phaseOf((JsonNode) world.notes.get("input"))).as("la entrada sigue en transformación").isEqualTo("IN_TRANSFORMATION");
        assertThat(count("SELECT count(*) FROM batches")).as("no nació ningún lote de salida").isEqualTo(1);
        assertThat(outbox.ofType("TransformationCompleted")).isEmpty();
    }

    @Y("^comunica que las cantidades no son válidas\\.$")
    public void itSaysTheQuantitiesAreNotValid() {
        assertThat(world.last.code()).isEqualTo("INVALID_QUANTITIES");
        assertThat(world.last.message()).contains("cantidades no son válidas");
    }

    // ---- US19 ----

    @Dado("^que una transformación genera desperdicio$")
    public void aTransformationGeneratesWaste() {
        JsonNode input = registerLot(100);
        world.notes.put("input", input);
        startTransformation("DYEING", registerMachine(), input);
    }

    @Dado("^el operario registra una cantidad mayor a cero$")
    public void theOperatorRegistersAQuantityGreaterThanZero() {
        prepareCompletion(map("outputs", outputs(90), "wasteKg", 5));
    }

    @Entonces("^el sistema solicita el motivo$")
    public void theSystemAsksForTheReason() {
        assertThat(world.last.status()).as("desperdicio sin motivo: %s", world.last.raw()).isEqualTo(400);
        assertThat(world.last.code()).isEqualTo("WASTE_REASON_REQUIRED");
        assertThat(world.last.message()).contains("motivo");
        assertThat(api.get(world.operatorA, "/api/transformations/" + id(world.transformation)).text("status")).as("nada se finalizó").isEqualTo("IN_PROGRESS");
    }

    @Y("^conserva cantidad y motivo como parte del balance\\.$")
    public void itKeepsQuantityAndReasonAsPartOfTheBalance() {
        world.body.put("wasteReason", "Recortes de orillo");
        Reply completed = world.send(() -> completeTransformation(world.transformation, world.body));
        assertThat(completed.status()).as("con el motivo: %s", completed.raw()).isEqualTo(200);
        Reply stored = api.get(world.operatorA, "/api/transformations/" + id(world.transformation));
        assertThat(stored.field("wasteKg").decimalValue()).isEqualByComparingTo("5");
        assertThat(stored.text("wasteReason")).isEqualTo("Recortes de orillo");
        assertThat(stored.field("shrinkageKg").decimalValue()).as("merma = 100 − 90 − 5").isEqualByComparingTo("5");
    }

    // ---- US20 ----

    @Dado("^que se genera un lote de salida$")
    public void anOutputBatchIsGenerated() {
        JsonNode input = registerLot(100);
        world.notes.put("input", input);
        startTransformation("DYEING", registerMachine(), input);
        prepareCompletion(map("outputs", outputs(90), "wasteKg", 5, "wasteReason", "Orillo"));
    }

    @Cuando("^el operario lo marca como producto final$")
    @SuppressWarnings("unchecked")
    public void theOperatorMarksItAsFinalProduct() {
        ((List<Map<String, Object>>) world.body.get("outputs")).get(0).put("finalProduct", true);
        world.submit();
    }

    @Entonces("^el sistema registra dicha condición$")
    public void theSystemRegistersThatCondition() {
        assertThat(world.last.status()).as("finalizar con producto final: %s", world.last.raw()).isEqualTo(200);
        JsonNode output = world.last.field("outputs").get(0);
        assertThat(output.path("finalProduct").asBoolean()).isTrue();
        assertThat(lot(world.operatorA, output).path("finalProduct").asBoolean()).isTrue();
        world.batch = output;
    }

    @Y("^mantiene el lote dentro de su genealogía\\.$")
    public void itKeepsTheBatchInItsGenealogy() {
        JsonNode stored = lot(world.operatorA, world.batch);
        assertThat(stored.path("originType").asString()).isEqualTo("TRANSFORMATION");
        assertThat(stored.path("phase").asString()).isEqualTo("AVAILABLE");
        Reply transformation = api.get(world.operatorA, "/api/transformations/" + id(world.transformation));
        assertThat(transformation.field("outputBatchIds")).extracting(JsonNode::asString).contains(world.batch.path("id").asString());
        // la arista entrada → salida llega a Traceability en TransformationCompleted, con la salida marcada como producto final
        List<Outbox.Event> completed = outbox.ofType("TransformationCompleted", id(world.transformation).toString());
        assertThat(completed).hasSize(1);
        JsonNode published = completed.get(0).payload();
        assertThat(published.path("inputs").get(0).path("batchId").asString()).isEqualTo(((JsonNode) world.notes.get("input")).path("id").asString());
        assertThat(published.path("outputs").get(0).path("batchId").asString()).isEqualTo(world.batch.path("id").asString());
        assertThat(published.path("outputs").get(0).path("finalProduct").asBoolean()).isTrue();
        assertThat(api.get(world.operatorA, "/api/batches/qr/" + world.batch.path("qrCode").asString()).status()).isEqualTo(200);
    }

    private static final Map<String, Object> COMMERCIAL = map("buyerOrDistributor", "Tiendas Lima SAC", "salePrice", 99.9, "currency", "USD",
            "commercialExitDate", "2026-11-03", "commercialReference", "OC-2026-0457");

    @Dado("^que un lote se marca como producto final$")
    public void aBatchIsMarkedAsFinalProduct() {
        world.batch = finalProduct();
    }

    @Cuando("^el operario registra distribuidor o comprador, precio, moneda, fecha de salida comercial o referencia comercial$")
    public void theOperatorRegistersTheCommercialData() {
        world.send(() -> api.put(world.operatorA, "/api/batches/" + id(world.batch) + "/commercial-data", COMMERCIAL));
    }

    @Entonces("^el sistema conserva los datos ingresados como información Privada\\.$")
    public void theSystemKeepsTheDataAsPrivateInformation() {
        assertThat(world.last.status()).as("datos comerciales: %s", world.last.raw()).isEqualTo(200);
        JsonNode own = lot(world.operatorA, world.batch).path("commercialData");
        assertThat(own.path("buyerOrDistributor").asString()).isEqualTo("Tiendas Lima SAC");
        assertThat(own.path("salePrice").decimalValue()).isEqualByComparingTo("99.9");
        assertThat(own.path("currency").asString()).isEqualTo("USD");
        assertThat(own.path("commercialExitDate").asString()).isEqualTo("2026-11-03");
        assertThat(own.path("commercialReference").asString()).isEqualTo("OC-2026-0457");
        // Privada: otra empresa no los ve (ni por QR, ni por el detalle) y no viajan en ningún evento
        Reply sharedView = api.get(world.operatorB, "/api/batches/qr/" + world.batch.path("qrCode").asString());
        assertThat(sharedView.text("access")).isEqualTo("SHARED");
        assertThat(sharedView.raw()).doesNotContain("Tiendas Lima").doesNotContain("OC-2026-0457").doesNotContain("commercialData").doesNotContain("salePrice");
        assertThat(api.get(world.operatorB, "/api/batches/" + id(world.batch)).status()).isEqualTo(404);
        String allEvents = outbox.all().stream().map(event -> event.envelope().toString()).reduce("", String::concat);
        assertThat(allEvents).doesNotContain("Tiendas Lima").doesNotContain("OC-2026-0457").doesNotContain("buyerOrDistributor")
                .doesNotContain("salePrice").doesNotContain("commercialReference");
    }

    @Dado("^que se registra un precio de venta$")
    public void aSalePriceIsRegistered() {
        world.batch = finalProduct();
        world.body = map("buyerOrDistributor", "Tiendas Lima SAC", "salePrice", 120.5);
        world.pending = () -> api.put(world.operatorA, "/api/batches/" + id(world.batch) + "/commercial-data", world.body);
    }

    @Entonces("^el sistema solicita completar la moneda\\.$")
    public void theSystemAsksToCompleteTheCurrency() {
        assertThat(world.last.status()).as("precio sin moneda: %s", world.last.raw()).isEqualTo(400);
        assertThat(world.last.code()).isEqualTo("PRICE_REQUIRES_CURRENCY");
        assertThat(world.last.message()).contains("moneda");
        assertThat(lot(world.operatorA, world.batch).path("commercialData").path("salePrice").isNull()).as("no se guardó nada").isTrue();
    }

    @Dado("^que un lote está marcado como producto final$")
    public void aBatchIsMarkedFinalProduct() {
        world.batch = finalProduct();
        world.notes.put("other", registerLot(30));
    }

    @Cuando("^se intenta dividirlo, transformarlo, transferirlo o incorporarlo nuevamente al recorrido productivo$")
    public void someoneTriesToSplitTransformTransferOrReincorporateIt() {
        String finalId = id(world.batch).toString();
        String otherId = id((JsonNode) world.notes.get("other")).toString();
        String machine = id(registerMachine()).toString();
        world.replies.clear();
        world.send(() -> api.post(world.operatorA, "/api/batches/" + finalId + "/split", map("children", List.of(map("quantityKg", 40), map("quantityKg", 50)))));
        world.send(() -> api.post(world.operatorA, "/api/transformations", map("type", "DYEING", "machineId", machine, "batchIds", List.of(finalId))));
        world.send(() -> api.post(world.operatorA, "/api/transfers", map("targetCompanyId", World.COMPANY_B.toString(), "batchIds", List.of(finalId))));
        world.send(() -> api.post(world.operatorA, "/api/transformations", map("type", "DYEING", "machineId", machine, "batchIds", List.of(otherId, finalId))));
    }

    @Entonces("^el sistema no permite la operación\\.$")
    public void theSystemDoesNotAllowTheOperation() {
        assertThat(world.replies).hasSize(4);
        for (Reply reply : world.replies) {
            assertThat(reply.status()).as("operar un producto final: %s", reply.raw()).isEqualTo(409);
            assertThat(reply.code()).isEqualTo("BATCH_IS_FINAL_PRODUCT");
        }
        assertThat(lot(world.operatorA, world.batch).path("phase").asString()).isEqualTo("AVAILABLE");
        assertThat(phaseOf((JsonNode) world.notes.get("other"))).as("el lote que se quiso mezclar tampoco se movió").isEqualTo("AVAILABLE");
        assertThat(count("SELECT count(*) FROM transfers")).isZero();
        assertThat(count("SELECT count(*) FROM transformations")).as("solo la que lo produjo").isEqualTo(1);
    }
}
