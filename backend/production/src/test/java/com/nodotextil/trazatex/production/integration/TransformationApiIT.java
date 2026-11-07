package com.nodotextil.trazatex.production.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US16 iniciar, US17 composición, US18 balance, US19 desperdicio y US20 producto final. */
class TransformationApiIT extends ApiTestBase {

    private UUID machine;

    private UUID machine() throws Exception {
        if (machine == null) {
            machine = idOf(registerMachine("MQ-01"));
        }
        return machine;
    }

    private String phaseOf(JsonNode batch) throws Exception {
        return read(getAs(OPERATOR_OF_A, "/api/batches/" + batch.path("id").asString())).path("phase").asString();
    }

    // ---- US16 iniciar ----

    @Test
    void startingRegistersTheStartTimeAndMovesTheBatchesToInTransformation() throws Exception {
        JsonNode a = registerBatch(100);
        JsonNode b = registerBatch(50);
        clock.advance(Duration.ofHours(1));

        JsonNode transformation = startTransformation("SPINNING", machine(), idOf(a), idOf(b));

        assertThat(transformation.path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(transformation.path("type").asString()).isEqualTo("SPINNING");
        assertThat(transformation.path("mix").asBoolean()).isTrue();
        assertThat(transformation.path("startTime").asString()).isEqualTo(START.plus(Duration.ofHours(1)).toString());
        assertThat(transformation.path("endTime").isNull()).isTrue();
        assertThat(transformation.path("operatorId").asString()).isEqualTo(OPERATOR_A.toString());
        assertThat(transformation.path("totalInputKg").decimalValue()).isEqualByComparingTo("150");
        assertThat(transformation.path("inputs").get(0).path("batchId").asString()).as("en el orden pedido").isEqualTo(a.path("id").asString());
        assertThat(transformation.path("inputs").get(1).path("batchId").asString()).isEqualTo(b.path("id").asString());
        assertThat(phaseOf(a)).isEqualTo("IN_TRANSFORMATION");
        assertThat(phaseOf(b)).isEqualTo("IN_TRANSFORMATION");
    }

    @Test
    void aBatchThatIsNotEnabledBlocksTheStartAndNothingMoves() throws Exception {
        JsonNode ok = registerBatch(100);
        JsonNode held = registerBatch(10);
        jdbc.sql("UPDATE batches SET quality_hold = TRUE WHERE id = :id").param("id", idOf(held)).update();

        ResultActions result = postAs(OPERATOR_OF_A, "/api/transformations", obj("type", "SPINNING", "machineId", machine(),
                "batchIds", List.of(idOf(ok), idOf(held)))).andExpect(status().isConflict());

        assertThat(code(result)).isEqualTo("BATCH_ON_QUALITY_HOLD");
        assertThat(phaseOf(ok)).isEqualTo("AVAILABLE");
        assertThat(count("SELECT count(*) FROM transformations")).isZero();
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'TRANSFORMATION_STARTED'")).isZero();
    }

    @Test
    void aMachineThatIsNotActiveCannotBeUsed() throws Exception {
        JsonNode batch = registerBatch(100);
        patchAs(ADMIN_OF_A, "/api/machines/" + machine() + "/status", obj("status", "OUT_OF_SERVICE")).andExpect(status().isOk());

        ResultActions result = postAs(OPERATOR_OF_A, "/api/transformations", obj("type", "SPINNING", "machineId", machine(),
                "batchIds", List.of(idOf(batch)))).andExpect(status().isConflict());

        assertThat(code(result)).isEqualTo("MACHINE_NOT_ACTIVE");
        assertThat(phaseOf(batch)).isEqualTo("AVAILABLE");

        patchAs(ADMIN_OF_A, "/api/machines/" + machine() + "/status", obj("status", "INACTIVE")).andExpect(status().isOk());
        assertThat(code(postAs(OPERATOR_OF_A, "/api/transformations", obj("type", "SPINNING", "machineId", machine(),
                "batchIds", List.of(idOf(batch)))).andExpect(status().isConflict()))).isEqualTo("MACHINE_NOT_ACTIVE");
    }

    @Test
    void theMachineAndTheBatchesMustBelongToTheCompany() throws Exception {
        JsonNode own = registerBatch(100);
        JsonNode foreignBatch = registerBatchAs(OPERATOR_OF_B, 100);
        JsonNode foreignMachine = read(postAs(ADMIN_OF_B, "/api/machines", obj("internalCode", "B-1", "name", "De B", "type", "Cardado"))
                .andExpect(status().isCreated()));

        assertThat(code(postAs(OPERATOR_OF_A, "/api/transformations", obj("type", "SPINNING", "machineId", idOf(foreignMachine),
                "batchIds", List.of(idOf(own)))).andExpect(status().isNotFound()))).isEqualTo("MACHINE_NOT_FOUND");
        assertThat(code(postAs(OPERATOR_OF_A, "/api/transformations", obj("type", "SPINNING", "machineId", machine(),
                "batchIds", List.of(idOf(own), idOf(foreignBatch)))).andExpect(status().isNotFound()))).isEqualTo("BATCH_NOT_FOUND");
        postAs(OPERATOR_OF_A, "/api/transformations", obj("type", "SPINNING", "machineId", machine(),
                "batchIds", List.of(UUID.randomUUID()))).andExpect(status().isNotFound());

        assertThat(phaseOf(own)).isEqualTo("AVAILABLE");
        assertThat(count("SELECT count(*) FROM batches WHERE current_stage = 'IN_TRANSFORMATION'")).isZero();
    }

    @Test
    void theRequestMustBeComplete() throws Exception {
        JsonNode batch = registerBatch(100);

        assertThat(code(postAs(OPERATOR_OF_A, "/api/transformations", obj("machineId", machine(), "batchIds", List.of(idOf(batch))))
                .andExpect(status().isBadRequest()))).isEqualTo("MISSING_FIELDS");
        assertThat(code(postAs(OPERATOR_OF_A, "/api/transformations", obj("type", "SPINNING", "batchIds", List.of(idOf(batch))))
                .andExpect(status().isBadRequest()))).isEqualTo("MISSING_FIELDS");
        assertThat(code(postAs(OPERATOR_OF_A, "/api/transformations", obj("type", "SPINNING", "machineId", machine(), "batchIds", List.of()))
                .andExpect(status().isBadRequest()))).isEqualTo("NO_BATCHES_SELECTED");
        assertThat(code(postAs(OPERATOR_OF_A, "/api/transformations", obj("type", "SPINNING", "machineId", machine(),
                "batchIds", List.of(idOf(batch), idOf(batch)))).andExpect(status().isBadRequest()))).isEqualTo("DUPLICATE_BATCH");
        postAs(OPERATOR_OF_A, "/api/transformations", obj("type", "FINISHING", "machineId", machine(), "batchIds", List.of(idOf(batch))))
                .andExpect(status().isBadRequest());
        assertThat(phaseOf(batch)).isEqualTo("AVAILABLE");
    }

    // ---- US18 finalizar, US19 desperdicio, US17 composición ----

    @Test
    void completingComputesTheShrinkageProcessesTheInputsAndCreatesAvailableOutputs() throws Exception {
        JsonNode input = registerBatch(100);
        UUID id = idOf(startTransformation("SPINNING", machine(), idOf(input)));
        clock.advance(Duration.ofHours(2));

        JsonNode result = read(postAs(OPERATOR_OF_A, "/api/transformations/" + id + "/complete", obj(
                "outputs", List.of(output("Hilo", 70), obj("name", "Hilo B", "materialType", "Hilo", "quantityKg", 10.25)),
                "wasteKg", 5, "wasteReason", "Fibra contaminada")).andExpect(status().isOk()));

        JsonNode transformation = result.path("transformation");
        assertThat(transformation.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(transformation.path("totalOutputKg").decimalValue()).isEqualByComparingTo("80.25");
        assertThat(transformation.path("wasteKg").decimalValue()).isEqualByComparingTo("5");
        assertThat(transformation.path("wasteReason").asString()).isEqualTo("Fibra contaminada");
        assertThat(transformation.path("shrinkageKg").decimalValue()).as("100 − 80,25 − 5").isEqualByComparingTo("14.75");
        assertThat(transformation.path("endTime").asString()).isEqualTo(START.plus(Duration.ofHours(2)).toString());
        assertThat(transformation.path("outputBatchIds")).hasSize(2);
        assertThat(result.path("outputs")).hasSize(2);
        for (JsonNode out : result.path("outputs")) {
            assertThat(out.path("phase").asString()).isEqualTo("AVAILABLE");
            assertThat(out.path("qualityStatus").asString()).isEqualTo("NOT_REVIEWED");
            assertThat(out.path("originType").asString()).isEqualTo("TRANSFORMATION");
            assertThat(out.path("traceabilityId").asString()).isNotEqualTo(input.path("traceabilityId").asString());
            assertThat(out.path("composition")).as("una entrada: misma composición").isEqualTo(input.path("composition"));
        }
        assertThat(result.path("outputs").get(1).path("name").asString()).isEqualTo("Hilo B");
        assertThat(phaseOf(input)).isEqualTo("PROCESSED");
        getAs(OPERATOR_OF_A, "/api/batches/" + result.path("outputs").get(0).path("id").asString()).andExpect(status().isOk());
    }

    @Test
    void anUnbalancedTransformationCannotBeCompleted() throws Exception {
        JsonNode input = registerBatch(100);
        UUID id = idOf(startTransformation("SPINNING", machine(), idOf(input)));

        ResultActions result = postAs(OPERATOR_OF_A, "/api/transformations/" + id + "/complete", obj(
                "outputs", List.of(output("Hilo", 98)), "wasteKg", 3, "wasteReason", "x")).andExpect(status().isBadRequest());

        assertThat(code(result)).isEqualTo("INVALID_QUANTITIES");
        assertThat(read(result).path("message").asString()).contains("cantidades no son válidas");
        assertThat(phaseOf(input)).isEqualTo("IN_TRANSFORMATION");
        assertThat(count("SELECT count(*) FROM batches")).isEqualTo(1);
        assertThat(read(getAs(OPERATOR_OF_A, "/api/transformations/" + id)).path("status").asString()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void wasteNeedsAReason() throws Exception {
        JsonNode input = registerBatch(100);
        UUID id = idOf(startTransformation("SPINNING", machine(), idOf(input)));

        assertThat(code(postAs(OPERATOR_OF_A, "/api/transformations/" + id + "/complete", obj(
                "outputs", List.of(output("Hilo", 90)), "wasteKg", 2)).andExpect(status().isBadRequest()))).isEqualTo("WASTE_REASON_REQUIRED");
        assertThat(code(postAs(OPERATOR_OF_A, "/api/transformations/" + id + "/complete", obj(
                "outputs", List.of(output("Hilo", 90)), "wasteKg", 2, "wasteReason", "  ")).andExpect(status().isBadRequest())))
                .isEqualTo("WASTE_REASON_REQUIRED");
        assertThat(phaseOf(input)).isEqualTo("IN_TRANSFORMATION");
    }

    @Test
    void theMinimumYieldOfEachTypeIsEnforcedWithAClearMessage() throws Exception {
        JsonNode spinning = registerBatch(100);
        UUID s = idOf(startTransformation("SPINNING", machine(), idOf(spinning)));
        ResultActions low = postAs(OPERATOR_OF_A, "/api/transformations/" + s + "/complete", obj("outputs", List.of(output("Hilo", 59.9))))
                .andExpect(status().isBadRequest());
        assertThat(code(low)).isEqualTo("YIELD_BELOW_MINIMUM");
        assertThat(read(low).path("message").asString()).contains("59,9 %").contains("mínimo de 60 %").contains("hilandería");
        postAs(OPERATOR_OF_A, "/api/transformations/" + s + "/complete", obj("outputs", List.of(output("Hilo", 60)))).andExpect(status().isOk());

        JsonNode weaving = registerBatch(100);
        UUID w = idOf(startTransformation("WEAVING", machine(), idOf(weaving)));
        assertThat(code(postAs(OPERATOR_OF_A, "/api/transformations/" + w + "/complete", obj("outputs", List.of(output("Tela", 79.99))))
                .andExpect(status().isBadRequest()))).isEqualTo("YIELD_BELOW_MINIMUM");
        postAs(OPERATOR_OF_A, "/api/transformations/" + w + "/complete", obj("outputs", List.of(output("Tela", 80)))).andExpect(status().isOk());

        JsonNode dyeing = registerBatch(100);
        UUID d = idOf(startTransformation("DYEING", machine(), idOf(dyeing)));
        assertThat(read(postAs(OPERATOR_OF_A, "/api/transformations/" + d + "/complete", obj("outputs", List.of(output("Tela", 70))))
                .andExpect(status().isBadRequest())).path("message").asString()).contains("tintorería");
        assertThat(phaseOf(dyeing)).isEqualTo("IN_TRANSFORMATION");
    }

    @Test
    void severalInputsAreMixedByKgWeightedAverage() throws Exception {
        JsonNode cotton = read(postAs(OPERATOR_OF_A, "/api/batches", obj("materialType", "Fibra", "composition", List.of(component("Algodón", 100)),
                "quantityKg", 100, "origin", "Piura", "receptionCharacteristics", "ok")).andExpect(status().isCreated()));
        JsonNode half = read(postAs(OPERATOR_OF_A, "/api/batches", obj("materialType", "Fibra",
                "composition", List.of(component("Algodón", 50), component("Poliéster", 50)), "quantityKg", 300, "origin", "Piura",
                "receptionCharacteristics", "ok")).andExpect(status().isCreated()));
        UUID id = idOf(startTransformation("SPINNING", machine(), idOf(cotton), idOf(half)));

        JsonNode result = read(postAs(OPERATOR_OF_A, "/api/transformations/" + id + "/complete", obj("outputs", List.of(output("Hilo", 390))))
                .andExpect(status().isOk()));

        JsonNode composition = result.path("outputs").get(0).path("composition");
        assertThat(composition.get(0).path("name").asString()).isEqualTo("Algodón");
        assertThat(composition.get(0).path("percentage").decimalValue()).isEqualByComparingTo("62.5");
        assertThat(composition.get(1).path("percentage").decimalValue()).isEqualByComparingTo("37.5");
        assertThat(result.path("transformation").path("mix").asBoolean()).isTrue();
    }

    @Test
    void anOutputCanBeMarkedAsFinalProductWithPrivateCommercialData() throws Exception {
        JsonNode input = registerBatch(100);
        UUID id = idOf(startTransformation("DYEING", machine(), idOf(input)));

        JsonNode result = read(postAs(OPERATOR_OF_A, "/api/transformations/" + id + "/complete", obj("outputs", List.of(
                obj("name", "Tela teñida", "materialType", "Tela", "quantityKg", 90, "finalProduct", true, "commercialData",
                        obj("buyerOrDistributor", "Tiendas SAC", "salePrice", 1250.5, "currency", "PEN", "commercialReference", "OC-77")),
                obj("materialType", "Retazo", "quantityKg", 5)))).andExpect(status().isOk()));

        JsonNode finalProduct = result.path("outputs").get(0);
        assertThat(finalProduct.path("finalProduct").asBoolean()).isTrue();
        assertThat(finalProduct.path("commercialData").path("buyerOrDistributor").asString()).isEqualTo("Tiendas SAC");
        assertThat(finalProduct.path("commercialData").path("salePrice").decimalValue()).isEqualByComparingTo("1250.5");
        assertThat(result.path("outputs").get(1).path("finalProduct").asBoolean()).isFalse();
        assertThat(result.path("outputs").get(1).path("commercialData").path("buyerOrDistributor").isNull()).isTrue();

        // US20 esc. 4: un producto final ya no se divide, transforma ni transfiere
        String url = "/api/batches/" + finalProduct.path("id").asString();
        assertThat(code(postAs(OPERATOR_OF_A, url + "/split", obj("children", List.of(obj("quantityKg", 40), obj("quantityKg", 50))))
                .andExpect(status().isConflict()))).isEqualTo("BATCH_IS_FINAL_PRODUCT");
        assertThat(code(postAs(OPERATOR_OF_A, "/api/transformations", obj("type", "DYEING", "machineId", machine(),
                "batchIds", List.of(finalProduct.path("id").asString()))).andExpect(status().isConflict()))).isEqualTo("BATCH_IS_FINAL_PRODUCT");
        jdbc.sql("UPDATE company_projection SET status = 'ACTIVE'").update();
        assertThat(code(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B,
                "batchIds", List.of(finalProduct.path("id").asString()))).andExpect(status().isConflict()))).isEqualTo("BATCH_IS_FINAL_PRODUCT");
    }

    @Test
    void commercialDataCanBeEditedLaterAndAPriceNeedsItsCurrency() throws Exception {
        JsonNode input = registerBatch(100);
        UUID id = idOf(startTransformation("DYEING", machine(), idOf(input)));
        JsonNode finalProduct = read(postAs(OPERATOR_OF_A, "/api/transformations/" + id + "/complete", obj("outputs", List.of(
                obj("materialType", "Tela", "quantityKg", 90, "finalProduct", true)))).andExpect(status().isOk())).path("outputs").get(0);
        String url = "/api/batches/" + finalProduct.path("id").asString() + "/commercial-data";

        assertThat(code(putAs(OPERATOR_OF_A, url, obj("salePrice", 10)).andExpect(status().isBadRequest()))).isEqualTo("PRICE_REQUIRES_CURRENCY");
        JsonNode updated = read(putAs(OPERATOR_OF_A, url, obj("buyerOrDistributor", "Tiendas SAC", "salePrice", 99.9, "currency", "usd",
                "commercialExitDate", "2026-11-03", "commercialReference", "OC-1")).andExpect(status().isOk()));

        assertThat(updated.path("commercialData").path("currency").asString()).isEqualTo("USD");
        assertThat(updated.path("commercialData").path("commercialExitDate").asString()).isEqualTo("2026-11-03");
        assertThat(updated.path("commercialData").path("salePrice").decimalValue()).isEqualByComparingTo("99.9");
        putAs(ADMIN_OF_A, url, obj("buyerOrDistributor", "x")).andExpect(status().isForbidden());
        putAs(OPERATOR_OF_B, url, obj("buyerOrDistributor", "x")).andExpect(status().isNotFound());
    }

    @Test
    void commercialDataIsReplacedNotMerged() throws Exception {
        UUID id = idOf(startTransformation("DYEING", machine(), idOf(registerBatch(100))));
        JsonNode finalProduct = read(postAs(OPERATOR_OF_A, "/api/transformations/" + id + "/complete", obj("outputs", List.of(
                obj("materialType", "Tela", "quantityKg", 90, "finalProduct", true)))).andExpect(status().isOk())).path("outputs").get(0);
        String url = "/api/batches/" + finalProduct.path("id").asString() + "/commercial-data";
        putAs(OPERATOR_OF_A, url, obj("buyerOrDistributor", "Tiendas SAC", "salePrice", 99.9, "currency", "USD",
                "commercialExitDate", "2026-11-03", "commercialReference", "OC-1")).andExpect(status().isOk());

        JsonNode replaced = read(putAs(OPERATOR_OF_A, url, obj("buyerOrDistributor", "Otra SAC")).andExpect(status().isOk()));

        JsonNode data = replaced.path("commercialData");
        assertThat(data.path("buyerOrDistributor").asString()).isEqualTo("Otra SAC");
        assertThat(data.path("salePrice").isNull()).isTrue();
        assertThat(data.path("currency").isNull()).isTrue();
        assertThat(data.path("commercialExitDate").isNull()).isTrue();
        assertThat(data.path("commercialReference").isNull()).isTrue();
        patchAs(OPERATOR_OF_A, url, obj("buyerOrDistributor", "x")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void commercialDataForAnOutputThatIsNotAFinalProductIsRejected() throws Exception {
        JsonNode input = registerBatch(100);
        UUID id = idOf(startTransformation("DYEING", machine(), idOf(input)));

        assertThat(code(postAs(OPERATOR_OF_A, "/api/transformations/" + id + "/complete", obj("outputs", List.of(
                obj("materialType", "Tela", "quantityKg", 90, "commercialData", obj("buyerOrDistributor", "Tiendas SAC"))))).andExpect(status().isBadRequest())))
                .isEqualTo("INVALID_COMMERCIAL_DATA");
        assertThat(phaseOf(input)).isEqualTo("IN_TRANSFORMATION");
    }

    @Test
    void aTransformationCanOnlyBeCompletedOnce() throws Exception {
        JsonNode input = registerBatch(100);
        UUID id = idOf(startTransformation("SPINNING", machine(), idOf(input)));
        var body = obj("outputs", List.of(output("Hilo", 90)));
        postAs(OPERATOR_OF_A, "/api/transformations/" + id + "/complete", body).andExpect(status().isOk());

        assertThat(code(postAs(OPERATOR_OF_A, "/api/transformations/" + id + "/complete", body).andExpect(status().isConflict())))
                .isEqualTo("TRANSFORMATION_NOT_IN_PROGRESS");
        assertThat(count("SELECT count(*) FROM batches WHERE origin_type = 'TRANSFORMATION'")).isEqualTo(1);
    }

    @Test
    void anOutputNeedsAQuantityAndTheOutputsCannotBeEmpty() throws Exception {
        JsonNode input = registerBatch(100);
        UUID id = idOf(startTransformation("SPINNING", machine(), idOf(input)));
        String url = "/api/transformations/" + id + "/complete";

        assertThat(code(postAs(OPERATOR_OF_A, url, obj("outputs", List.of())).andExpect(status().isBadRequest()))).isEqualTo("MISSING_FIELDS");
        assertThat(code(postAs(OPERATOR_OF_A, url, obj()).andExpect(status().isBadRequest()))).isEqualTo("MISSING_FIELDS");
        assertThat(code(postAs(OPERATOR_OF_A, url, obj("outputs", List.of(obj("materialType", "Hilo")))).andExpect(status().isBadRequest())))
                .isEqualTo("INVALID_QUANTITY");
        assertThat(code(postAs(OPERATOR_OF_A, url, obj("outputs", List.of(obj("quantityKg", 80)))).andExpect(status().isBadRequest())))
                .isEqualTo("MISSING_FIELDS");
        assertThat(code(postAs(OPERATOR_OF_A, url, obj("outputs", List.of(output("Hilo", 80)), "wasteKg", -1)).andExpect(status().isBadRequest())))
                .isEqualTo("INVALID_QUANTITY");
        assertThat(phaseOf(input)).isEqualTo("IN_TRANSFORMATION");
    }

    // ---- consulta ----

    @Test
    void transformationsAreListedAndReadOnlyByTheirCompany() throws Exception {
        UUID first = idOf(startTransformation("SPINNING", machine(), idOf(registerBatch(10))));
        clock.advance(Duration.ofMinutes(1));
        UUID second = idOf(startTransformation("DYEING", machine(), idOf(registerBatch(10))));
        postAs(OPERATOR_OF_A, "/api/transformations/" + first + "/complete", obj("outputs", List.of(output("Hilo", 9)))).andExpect(status().isOk());

        JsonNode all = read(getAs(ADMIN_OF_A, "/api/transformations").andExpect(status().isOk()));
        assertThat(all.path("items").get(0).path("id").asString()).as("la más reciente primero").isEqualTo(second.toString());
        assertThat(all.path("totalElements").asLong()).isEqualTo(2);
        assertThat(read(getAs(OPERATOR_OF_A, "/api/transformations?status=COMPLETED")).path("items")).hasSize(1);
        assertThat(read(getAs(OPERATOR_OF_A, "/api/transformations?type=DYEING")).path("items")).hasSize(1);
        assertThat(read(getAs(OPERATOR_OF_A, "/api/transformations?size=1&page=1")).path("items").get(0).path("id").asString()).isEqualTo(first.toString());
        assertThat(read(getAs(OPERATOR_OF_A, "/api/transformations?size=1000")).path("size").asInt()).isEqualTo(100);
        getAs(OPERATOR_OF_A, "/api/transformations?size=0").andExpect(status().isBadRequest());
        assertThat(read(getAs(OPERATOR_OF_B, "/api/transformations")).path("items")).isEmpty();
        getAs(OPERATOR_OF_B, "/api/transformations/" + first).andExpect(status().isNotFound());
        postAs(OPERATOR_OF_B, "/api/transformations/" + first + "/complete", obj("outputs", List.of(output("Hilo", 9)))).andExpect(status().isNotFound());
        getAs(OPERATOR_OF_A, "/api/transformations/" + first).andExpect(status().isOk());
    }
}
