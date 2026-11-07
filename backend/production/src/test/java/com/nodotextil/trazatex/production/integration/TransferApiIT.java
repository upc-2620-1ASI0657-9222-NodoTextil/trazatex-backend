package com.nodotextil.trazatex.production.integration;

import com.nodotextil.trazatex.production.domain.model.CompanyStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US21 iniciar, US22 resolver. */
class TransferApiIT extends ApiTestBase {

    private JsonNode transfer(JsonNode... batches) throws Exception {
        return read(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B,
                "batchIds", java.util.Arrays.stream(batches).map(this::idOf).toList())).andExpect(status().isCreated()));
    }

    private String phaseOf(JsonNode batch) throws Exception {
        return read(getAs(OPERATOR_OF_A, "/api/batches/" + batch.path("id").asString()).andExpect(status().isOk())).path("phase").asString();
    }

    // ---- US21 esc. 2: solo lotes completos ----

    private int transferStatus(Object body) throws Exception {
        return postAs(OPERATOR_OF_A, "/api/transfers", body).andReturn().getResponse().getStatus();
    }

    @Test
    void askingToTransferPartOfABatchIs400AskingToSplitFirstAndNothingChanges() throws Exception {
        JsonNode whole = registerBatch(10);
        JsonNode partial = registerBatch(12.5);
        long outboxBefore = count("SELECT count(*) FROM outbox");
        long auditBefore = count("SELECT count(*) FROM audit_log");

        ResultActions result = postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(whole), idOf(partial)),
                "quantitiesKg", obj(idOf(whole).toString(), 10, idOf(partial).toString(), 5))).andExpect(status().isBadRequest());

        JsonNode error = read(result);
        assertThat(error.path("code").asString()).isEqualTo("PARTIAL_TRANSFER_NOT_ALLOWED");
        assertThat(error.path("message").asString()).isEqualTo("Solo se transfieren lotes completos: divida el lote antes");
        assertThat(error.path("details")).extracting(JsonNode::asString).containsExactly(partial.path("traceabilityId").asString());
        assertThat(phaseOf(whole)).isEqualTo("AVAILABLE");
        assertThat(phaseOf(partial)).isEqualTo("AVAILABLE");
        assertThat(count("SELECT count(*) FROM transfers")).isZero();
        assertThat(count("SELECT count(*) FROM outbox")).as("un 400 no deja eventos").isEqualTo(outboxBefore);
        assertThat(count("SELECT count(*) FROM audit_log")).isEqualTo(auditBefore);
    }

    @Test
    void aValueEqualToTheQuantityOfTheBatchPassesEvenWithOtherDecimals() throws Exception {
        JsonNode batch = registerBatch(12.5);

        JsonNode created = read(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(batch)),
                "quantitiesKg", obj(idOf(batch).toString(), 12.500))).andExpect(status().isCreated()));

        assertThat(created.path("status").asString()).isEqualTo("PENDING");
        assertThat(phaseOf(batch)).isEqualTo("IN_TRANSFER");
        JsonNode second = registerBatch(10);
        assertThat(transferStatus(obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(second)), "quantitiesKg", obj(idOf(second).toString(), "10.000")))).isEqualTo(201);
    }

    @Test
    void afterSplittingTheChildCanBeTransferredByItsOwnQuantity() throws Exception {
        JsonNode parent = registerBatch(10);
        JsonNode split = read(postAs(OPERATOR_OF_A, "/api/batches/" + idOf(parent) + "/split",
                obj("children", List.of(obj("quantityKg", 4), obj("quantityKg", 6)))).andExpect(status().isCreated()));
        UUID child = idOf(split.path("children").get(0));

        assertThat(transferStatus(obj("targetCompanyId", COMPANY_B, "batchIds", List.of(child), "quantitiesKg", obj(child.toString(), 6)))).as("más que el hijo").isEqualTo(400);
        assertThat(transferStatus(obj("targetCompanyId", COMPANY_B, "batchIds", List.of(child), "quantitiesKg", obj(child.toString(), 4)))).isEqualTo(201);
    }

    @Test
    void aQuantityForABatchOutsideBatchIdsOrAnInvalidQuantityIs400() throws Exception {
        JsonNode batch = registerBatch(10);
        JsonNode other = registerBatch(10);

        ResultActions outside = postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(batch)),
                "quantitiesKg", obj(idOf(other).toString(), 10))).andExpect(status().isBadRequest());
        assertThat(code(outside)).isEqualTo("INVALID_QUANTITIES");
        assertThat(code(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(batch)),
                "quantitiesKg", obj(UUID.randomUUID().toString(), 10))).andExpect(status().isBadRequest()))).isEqualTo("INVALID_QUANTITIES");
        for (Object invalid : new Object[]{0, -3, 1.0005, null}) {
            assertThat(code(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(batch)),
                    "quantitiesKg", obj(idOf(batch).toString(), invalid))).andExpect(status().isBadRequest()))).as(String.valueOf(invalid)).isEqualTo("INVALID_QUANTITY");
        }
        assertThat(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(batch)),
                "quantitiesKg", obj("no-es-un-uuid", 10))).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(phaseOf(batch)).isEqualTo("AVAILABLE");
        assertThat(count("SELECT count(*) FROM transfers")).isZero();
    }

    @Test
    void aRejected400LeavesNeitherOutboxRowsNorIdempotencyKeysAndTheKeyCanBeReused() throws Exception {
        JsonNode batch = registerBatch(10);
        long outboxBefore = count("SELECT count(*) FROM outbox");
        String key = UUID.randomUUID().toString();

        for (Object quantities : new Object[]{obj(idOf(batch).toString(), 3), obj(UUID.randomUUID().toString(), 10), obj(idOf(batch).toString(), 0)}) {
            postWithKey(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(batch)), "quantitiesKg", quantities), key)
                    .andExpect(status().isBadRequest());
        }

        assertThat(count("SELECT count(*) FROM outbox")).isEqualTo(outboxBefore);
        assertThat(count("SELECT count(*) FROM idempotency_keys")).isZero();
        postWithKey(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(batch)),
                "quantitiesKg", obj(idOf(batch).toString(), 10)), key).andExpect(status().isCreated());
        assertThat(count("SELECT count(*) FROM idempotency_keys")).isEqualTo(1);
    }

    @Test
    void withoutQuantitiesTheTransferIsOfTheWholeBatchesAsBefore() throws Exception {
        JsonNode batch = registerBatch(10);

        assertThat(transferStatus(obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(batch))))).isEqualTo(201);
    }

    // ---- US21 iniciar ----

    @Test
    void initiatingRegistersTheDepartureAndMovesTheBatchesToInTransfer() throws Exception {
        JsonNode a = registerBatch(10);
        JsonNode b = registerBatch(20);
        clock.advance(Duration.ofMinutes(30));

        JsonNode transfer = transfer(a, b);

        assertThat(transfer.path("status").asString()).isEqualTo("PENDING");
        assertThat(transfer.path("originCompanyId").asString()).isEqualTo(COMPANY_A.toString());
        assertThat(transfer.path("targetCompanyId").asString()).isEqualTo(COMPANY_B.toString());
        assertThat(transfer.path("departureTime").asString()).isEqualTo(START.plus(Duration.ofMinutes(30)).toString());
        assertThat(transfer.path("initiatedBy").asString()).isEqualTo(OPERATOR_A.toString());
        assertThat(transfer.path("batchIds")).hasSize(2);
        assertThat(transfer.path("receptionTime").isNull()).isTrue();
        assertThat(phaseOf(a)).isEqualTo("IN_TRANSFER");
        assertThat(phaseOf(b)).isEqualTo("IN_TRANSFER");
    }

    @Test
    void aBatchThatIsNotEnabledBlocksTheWholeTransfer() throws Exception {
        JsonNode ok = registerBatch(10);
        JsonNode held = registerBatch(10);
        jdbc.sql("UPDATE batches SET quality_hold = TRUE WHERE id = :id").param("id", idOf(held)).update();

        ResultActions result = postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B,
                "batchIds", List.of(idOf(ok), idOf(held)))).andExpect(status().isConflict());

        assertThat(code(result)).isEqualTo("BATCH_ON_QUALITY_HOLD");
        assertThat(phaseOf(ok)).isEqualTo("AVAILABLE");
        assertThat(count("SELECT count(*) FROM transfers")).isZero();
    }

    @Test
    void theTargetMustExistBeActiveAndBeAnotherCompany() throws Exception {
        JsonNode batch = registerBatch(10);
        UUID unknown = UUID.randomUUID();

        assertThat(code(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", unknown, "batchIds", List.of(idOf(batch))))
                .andExpect(status().isNotFound()))).isEqualTo("TARGET_COMPANY_NOT_FOUND");
        assertThat(code(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_A, "batchIds", List.of(idOf(batch))))
                .andExpect(status().isBadRequest()))).isEqualTo("SAME_COMPANY_TRANSFER");
        saveCompany(COMPANY_B, CompanyStatus.INACTIVE);
        assertThat(code(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(batch))))
                .andExpect(status().isConflict()))).isEqualTo("TARGET_COMPANY_INACTIVE");
        assertThat(phaseOf(batch)).isEqualTo("AVAILABLE");
        assertThat(count("SELECT count(*) FROM transfers")).isZero();
    }

    @Test
    void theRequestMustBeCompleteAndTheBatchesMustBeOwn() throws Exception {
        JsonNode own = registerBatch(10);
        JsonNode foreign = registerBatchAs(OPERATOR_OF_B, 10);

        assertThat(code(postAs(OPERATOR_OF_A, "/api/transfers", obj("batchIds", List.of(idOf(own)))).andExpect(status().isBadRequest())))
                .isEqualTo("MISSING_FIELDS");
        assertThat(code(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of()))
                .andExpect(status().isBadRequest()))).isEqualTo("NO_BATCHES_SELECTED");
        assertThat(code(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(own), idOf(own))))
                .andExpect(status().isBadRequest()))).isEqualTo("DUPLICATE_BATCH");
        assertThat(code(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(own), idOf(foreign))))
                .andExpect(status().isNotFound()))).isEqualTo("BATCH_NOT_FOUND");
        assertThat(phaseOf(own)).isEqualTo("AVAILABLE");
    }

    // ---- US22 resolver ----

    @Test
    void acceptingMovesTheResponsibilityKeepsTheCodesAndRegistersTheReception() throws Exception {
        JsonNode batch = registerBatch(10);
        JsonNode initiated = transfer(batch);
        clock.advance(Duration.ofHours(3));

        JsonNode accepted = read(postAs(ADMIN_OF_B, "/api/transfers/" + initiated.path("id").asString() + "/accept").andExpect(status().isOk()));

        assertThat(accepted.path("status").asString()).isEqualTo("ACCEPTED");
        assertThat(accepted.path("receptionTime").asString()).isEqualTo(START.plus(Duration.ofHours(3)).toString());
        assertThat(accepted.path("resolvedBy").asString()).isEqualTo(ADMIN_B.toString());
        JsonNode moved = read(getAs(OPERATOR_OF_B, "/api/batches/" + batch.path("id").asString()).andExpect(status().isOk()));
        assertThat(moved.path("companyId").asString()).isEqualTo(COMPANY_B.toString());
        assertThat(moved.path("phase").asString()).isEqualTo("AVAILABLE");
        assertThat(moved.path("traceabilityId").asString()).isEqualTo(batch.path("traceabilityId").asString());
        assertThat(moved.path("qrCode").asString()).isEqualTo(batch.path("qrCode").asString());
        getAs(OPERATOR_OF_A, "/api/batches/" + batch.path("id").asString()).andExpect(status().isNotFound());
        // el nuevo dueño ya puede operarlo
        postAs(OPERATOR_OF_B, "/api/batches/" + batch.path("id").asString() + "/split",
                obj("children", List.of(obj("quantityKg", 4), obj("quantityKg", 6)))).andExpect(status().isCreated());
    }

    @Test
    void rejectingReturnsTheBatchesToTheOriginWithTheReason() throws Exception {
        JsonNode batch = registerBatch(10);
        JsonNode initiated = transfer(batch);
        clock.advance(Duration.ofMinutes(10));

        JsonNode rejected = read(postAs(ADMIN_OF_B, "/api/transfers/" + initiated.path("id").asString() + "/reject",
                obj("reason", "Calidad insuficiente")).andExpect(status().isOk()));

        assertThat(rejected.path("status").asString()).isEqualTo("REJECTED");
        assertThat(rejected.path("rejectionReason").asString()).isEqualTo("Calidad insuficiente");
        assertThat(rejected.path("receptionTime").isNull()).isTrue();
        assertThat(rejected.path("resolvedAt").asString()).isEqualTo(START.plus(Duration.ofMinutes(10)).toString());
        JsonNode back = read(getAs(OPERATOR_OF_A, "/api/batches/" + batch.path("id").asString()).andExpect(status().isOk()));
        assertThat(back.path("companyId").asString()).isEqualTo(COMPANY_A.toString());
        assertThat(back.path("phase").asString()).isEqualTo("AVAILABLE");
        getAs(OPERATOR_OF_B, "/api/batches/" + batch.path("id").asString()).andExpect(status().isNotFound());
    }

    @Test
    void rejectingNeedsAReason() throws Exception {
        JsonNode batch = registerBatch(10);
        String id = transfer(batch).path("id").asString();

        for (Object body : new Object[]{obj(), obj("reason", ""), obj("reason", "   ")}) {
            assertThat(code(postAs(ADMIN_OF_B, "/api/transfers/" + id + "/reject", body).andExpect(status().isBadRequest())))
                    .isEqualTo("REJECTION_REASON_REQUIRED");
        }
        assertThat(phaseOf(batch)).isEqualTo("IN_TRANSFER");
        assertThat(read(getAs(ADMIN_OF_B, "/api/transfers/" + id)).path("transfer").path("status").asString()).isEqualTo("PENDING");
    }

    @Test
    void onlyTheAdministratorOfTheTargetCompanyResolves() throws Exception {
        JsonNode batch = registerBatch(10);
        String id = transfer(batch).path("id").asString();

        postAs(OPERATOR_OF_B, "/api/transfers/" + id + "/accept").andExpect(status().isForbidden());
        assertThat(code(postAs(ADMIN_OF_A, "/api/transfers/" + id + "/accept").andExpect(status().isForbidden()))).isEqualTo("NOT_TRANSFER_RECIPIENT");
        assertThat(code(postAs(ADMIN_OF_A, "/api/transfers/" + id + "/reject", obj("reason", "x")).andExpect(status().isForbidden())))
                .isEqualTo("NOT_TRANSFER_RECIPIENT");
        assertThat(phaseOf(batch)).isEqualTo("IN_TRANSFER");
        assertThat(count("SELECT count(*) FROM transfers WHERE status = 'PENDING'")).isEqualTo(1);
    }

    @Test
    void aResolvedTransferCannotBeResolvedAgain() throws Exception {
        String accepted = transfer(registerBatch(10)).path("id").asString();
        postAs(ADMIN_OF_B, "/api/transfers/" + accepted + "/accept").andExpect(status().isOk());
        String rejected = transfer(registerBatch(10)).path("id").asString();
        postAs(ADMIN_OF_B, "/api/transfers/" + rejected + "/reject", obj("reason", "no")).andExpect(status().isOk());

        assertThat(code(postAs(ADMIN_OF_B, "/api/transfers/" + accepted + "/accept").andExpect(status().isConflict()))).isEqualTo("TRANSFER_NOT_PENDING");
        assertThat(code(postAs(ADMIN_OF_B, "/api/transfers/" + accepted + "/reject", obj("reason", "tarde")).andExpect(status().isConflict())))
                .isEqualTo("TRANSFER_NOT_PENDING");
        assertThat(code(postAs(ADMIN_OF_B, "/api/transfers/" + rejected + "/accept").andExpect(status().isConflict()))).isEqualTo("TRANSFER_NOT_PENDING");
    }

    @Test
    void aThirdCompanyDoesNotSeeTheTransferAtAll() throws Exception {
        UUID third = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
        saveCompany(third, CompanyStatus.ACTIVE);
        Caller outsiderAdmin = new Caller(UUID.randomUUID(), third, "COMPANY_ADMIN");
        String id = transfer(registerBatch(10)).path("id").asString();

        getAs(outsiderAdmin, "/api/transfers/" + id).andExpect(status().isNotFound());
        postAs(outsiderAdmin, "/api/transfers/" + id + "/accept").andExpect(status().isNotFound());
        assertThat(code(postAs(outsiderAdmin, "/api/transfers/" + id + "/reject", obj("reason", "x")).andExpect(status().isNotFound())))
                .isEqualTo("TRANSFER_NOT_FOUND");
        assertThat(read(getAs(outsiderAdmin, "/api/transfers")).path("items")).isEmpty();
        getAs(OPERATOR_OF_A, "/api/transfers/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    // ---- consulta ----

    @Test
    void theDetailShowsWhatIsBeingTransferredToBothCompanies() throws Exception {
        JsonNode batch = registerBatch(12.5);
        String id = transfer(batch).path("id").asString();

        for (Caller caller : List.of(OPERATOR_OF_A, ADMIN_OF_B)) {
            JsonNode detail = read(getAs(caller, "/api/transfers/" + id).andExpect(status().isOk()));
            assertThat(detail.path("transfer").path("id").asString()).isEqualTo(id);
            JsonNode lot = detail.path("batches").get(0);
            assertThat(lot.path("batchId").asString()).isEqualTo(batch.path("id").asString());
            assertThat(lot.path("traceabilityId").asString()).isEqualTo(batch.path("traceabilityId").asString());
            assertThat(lot.path("materialType").asString()).isEqualTo("Fibra");
            assertThat(lot.path("quantityKg").decimalValue()).isEqualByComparingTo("12.5");
            assertThat(lot.propertyNames()).doesNotContain("createdBy", "composition", "supplierName");
        }
    }

    @Test
    void theBatchSummaryHasExactlyTheFiveFieldsAndAFinalProductCannotBeTransferred() throws Exception {
        UUID id = idOf(startTransformation("DYEING", idOf(registerMachine("TJ-1")), idOf(registerBatch(100))));
        JsonNode outputs = read(postAs(OPERATOR_OF_A, "/api/transformations/" + id + "/complete", obj("outputs", List.of(
                obj("materialType", "Tela", "quantityKg", 50),
                obj("materialType", "Tela teñida", "quantityKg", 40, "finalProduct", true)))).andExpect(status().isOk())).path("outputs");
        JsonNode intermediate = outputs.get(0);
        JsonNode finalProduct = outputs.get(1);
        putAs(OPERATOR_OF_A, "/api/batches/" + idOf(finalProduct) + "/commercial-data", obj("buyerOrDistributor", "Tiendas SAC",
                "salePrice", 99.9, "currency", "USD")).andExpect(status().isOk());

        assertThat(code(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(finalProduct))))
                .andExpect(status().isConflict()))).isEqualTo("BATCH_IS_FINAL_PRODUCT");
        String transferId = transfer(intermediate).path("id").asString();

        for (Caller caller : List.of(OPERATOR_OF_A, ADMIN_OF_B)) {
            JsonNode lot = read(getAs(caller, "/api/transfers/" + transferId).andExpect(status().isOk())).path("batches").get(0);
            assertThat(lot.propertyNames()).containsExactlyInAnyOrder("batchId", "traceabilityId", "name", "materialType", "quantityKg");
        }
    }

    @Test
    void listsSeparateOutgoingAndIncomingTransfersNewestFirst() throws Exception {
        String sentOld = transfer(registerBatch(1)).path("id").asString();
        clock.advance(Duration.ofMinutes(1));
        String sentNew = transfer(registerBatch(1)).path("id").asString();
        JsonNode foreign = registerBatchAs(OPERATOR_OF_B, 1);
        clock.advance(Duration.ofMinutes(1));
        String received = read(postAs(OPERATOR_OF_B, "/api/transfers", obj("targetCompanyId", COMPANY_A, "batchIds", List.of(idOf(foreign))))
                .andExpect(status().isCreated())).path("id").asString();
        postAs(ADMIN_OF_B, "/api/transfers/" + sentOld + "/accept").andExpect(status().isOk());

        assertThat(ids("/api/transfers?direction=OUTGOING", OPERATOR_OF_A)).containsExactly(sentNew, sentOld);
        assertThat(ids("/api/transfers?direction=INCOMING", ADMIN_OF_A)).containsExactly(received);
        assertThat(ids("/api/transfers", ADMIN_OF_A)).containsExactly(received, sentNew, sentOld);
        assertThat(ids("/api/transfers?status=ACCEPTED", ADMIN_OF_A)).containsExactly(sentOld);
        assertThat(ids("/api/transfers?direction=INCOMING", ADMIN_OF_B)).containsExactly(sentNew, sentOld);
        assertThat(read(getAs(ADMIN_OF_A, "/api/transfers?size=1000")).path("size").asInt()).isEqualTo(100);
        getAs(ADMIN_OF_A, "/api/transfers?direction=SIDEWAYS").andExpect(status().isBadRequest());
        getAs(ADMIN_OF_A, "/api/transfers?page=-1").andExpect(status().isBadRequest());
    }

    private List<String> ids(String url, Caller caller) throws Exception {
        List<String> ids = new java.util.ArrayList<>();
        read(getAs(caller, url).andExpect(status().isOk())).path("items").forEach(i -> ids.add(i.path("id").asString()));
        return ids;
    }
}
