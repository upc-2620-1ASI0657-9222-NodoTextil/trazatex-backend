package com.nodotextil.trazatex.production.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US12 registrar, US13 buscar, US14 QR, US15 dividir (y la paginación de RNF-03). */
class BatchApiIT extends ApiTestBase {

    // ---- US12 registrar ----

    @Test
    void registeringCreatesAnAvailableNotReviewedBatchWithUniqueCodes() throws Exception {
        JsonNode batch = registerBatch(250.5);

        assertThat(batch.path("traceabilityId").asString()).matches(Pattern.compile("^TT-[0-9A-HJKMNP-TV-Z]{10}$"));
        assertThat(batch.path("qrCode").asString()).matches(Pattern.compile("^[0-9A-HJKMNP-TV-Z]{26}$"));
        assertThat(batch.path("phase").asString()).isEqualTo("AVAILABLE");
        assertThat(batch.path("qualityStatus").asString()).isEqualTo("NOT_REVIEWED");
        assertThat(batch.path("qualityHold").asBoolean()).isFalse();
        assertThat(batch.path("finalProduct").asBoolean()).isFalse();
        assertThat(batch.path("originType").asString()).isEqualTo("REGISTERED");
        assertThat(batch.path("companyId").asString()).isEqualTo(COMPANY_A.toString());
        assertThat(batch.path("createdBy").asString()).isEqualTo(OPERATOR_A.toString());
        assertThat(batch.path("quantityKg").decimalValue()).isEqualByComparingTo("250.5");
        assertThat(batch.path("composition")).hasSize(2);
        assertThat(batch.path("composition").get(0).path("percentage").decimalValue()).isEqualByComparingTo("70");
        assertThat(batch.path("supplierName").asString()).isEqualTo("Textiles del Norte");
        assertThat(batch.path("createdAt").asString()).isEqualTo(START.toString());
        assertThat(count("SELECT count(*) FROM batches")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM batch_compositions")).isEqualTo(2);
    }

    @Test
    void twoBatchesGetDifferentCodes() throws Exception {
        JsonNode first = registerBatch(10);
        JsonNode second = registerBatch(10);

        assertThat(first.path("traceabilityId").asString()).isNotEqualTo(second.path("traceabilityId").asString());
        assertThat(first.path("qrCode").asString()).isNotEqualTo(second.path("qrCode").asString());
    }

    @Test
    void theSupplierIsOptional() throws Exception {
        var body = batchBody(10);
        body.remove("supplierName");

        JsonNode batch = read(postAs(OPERATOR_OF_A, "/api/batches", body).andExpect(status().isCreated()));

        assertThat(batch.path("supplierName").isNull()).isTrue();
    }

    @Test
    void aCompositionThatDoesNotSum100IsRejectedAndNothingIsSaved() throws Exception {
        var body = batchBody(10);
        body.put("composition", List.of(component("Algodón", 70), component("Poliéster", 29.99)));

        ResultActions result = postAs(OPERATOR_OF_A, "/api/batches", body).andExpect(status().isBadRequest());

        assertThat(code(result)).isEqualTo("INVALID_COMPOSITION");
        assertThat(read(result).path("message").asString()).contains("composición no es válida");
        assertThat(count("SELECT count(*) FROM batches")).isZero();
        assertThat(count("SELECT count(*) FROM audit_log")).isZero();
    }

    @Test
    void anEmptyOrRepeatedCompositionIsInvalidToo() throws Exception {
        var empty = batchBody(10);
        empty.put("composition", List.of());
        assertThat(code(postAs(OPERATOR_OF_A, "/api/batches", empty).andExpect(status().isBadRequest()))).isEqualTo("INVALID_COMPOSITION");

        var repeated = batchBody(10);
        repeated.put("composition", List.of(component("Algodón", 50), component("algodón", 50)));
        assertThat(code(postAs(OPERATOR_OF_A, "/api/batches", repeated).andExpect(status().isBadRequest()))).isEqualTo("INVALID_COMPOSITION");
    }

    @Test
    void missingRequiredFieldsAreReportedTogether() throws Exception {
        ResultActions result = postAs(OPERATOR_OF_A, "/api/batches", obj("name", "x", "supplierName", "y")).andExpect(status().isBadRequest());

        assertThat(code(result)).isEqualTo("MISSING_FIELDS");
        List<String> details = new ArrayList<>();
        read(result).path("details").forEach(d -> details.add(d.asString()));
        assertThat(details).containsExactlyInAnyOrder("origin", "materialType", "composition", "quantity", "receptionCharacteristics");
    }

    @Test
    void anInvalidQuantityIsRejected() throws Exception {
        for (Object kg : new Object[]{0, -5, 1.2345, "abc"}) {
            var body = batchBody(10);
            body.put("quantityKg", kg);
            postAs(OPERATOR_OF_A, "/api/batches", body).andExpect(status().isBadRequest());
        }
        assertThat(count("SELECT count(*) FROM batches")).isZero();
    }

    @Test
    void malformedJsonIsA400WithTheStandardBody() throws Exception {
        ResultActions result = call(OPERATOR_OF_A, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/batches")
                .contentType("application/json").content("{no es json")).andExpect(status().isBadRequest());
        assertThat(code(result)).isEqualTo("MALFORMED_REQUEST");
        assertThat(read(result).has("timestamp")).isTrue();
    }

    // ---- consulta ----

    @Test
    void aBatchIsReadableByItsCompanyButNotByAnother() throws Exception {
        UUID id = idOf(registerBatch(10));

        getAs(OPERATOR_OF_A, "/api/batches/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
        getAs(ADMIN_OF_A, "/api/batches/" + id).andExpect(status().isOk());
        ResultActions foreign = getAs(OPERATOR_OF_B, "/api/batches/" + id).andExpect(status().isNotFound());
        assertThat(code(foreign)).isEqualTo("BATCH_NOT_FOUND");
        getAs(OPERATOR_OF_A, "/api/batches/" + UUID.randomUUID()).andExpect(status().isNotFound());
        getAs(OPERATOR_OF_A, "/api/batches/not-a-uuid").andExpect(status().isBadRequest());
    }

    // ---- US14 QR ----

    @Test
    void scanningAQrOfTheOwnCompanyShowsEverything() throws Exception {
        JsonNode batch = registerBatch(10);

        JsonNode result = read(getAs(OPERATOR_OF_A, "/api/batches/qr/" + batch.path("qrCode").asString()).andExpect(status().isOk()));

        assertThat(result.path("access").asString()).isEqualTo("OWN");
        assertThat(result.path("batch").path("id").asString()).isEqualTo(batch.path("id").asString());
        assertThat(result.path("batch").has("createdBy")).isTrue();
        assertThat(result.path("batch").has("composition")).isTrue();
    }

    @Test
    void scanningAQrOfAnotherCompanyShowsOnlyTheAlwaysSharedFields() throws Exception {
        JsonNode batch = registerBatch(10);

        JsonNode result = read(getAs(OPERATOR_OF_B, "/api/batches/qr/" + batch.path("qrCode").asString()).andExpect(status().isOk()));

        assertThat(result.path("access").asString()).isEqualTo("SHARED");
        JsonNode shared = result.path("batch");
        List<String> fields = new ArrayList<>();
        shared.propertyNames().forEach(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("batchId", "traceabilityId", "qrCode", "companyId", "materialType", "phase",
                "qualityStatus", "createdAt", "finalProduct");
        assertThat(shared.path("batchId").asString()).isEqualTo(batch.path("id").asString());
        assertThat(shared.path("companyId").asString()).isEqualTo(COMPANY_A.toString());
        assertThat(shared.path("traceabilityId").asString()).isEqualTo(batch.path("traceabilityId").asString());
        // nada privado ni configurable: ni operario, ni cantidad, ni composición, ni proveedor, ni origen, ni datos comerciales
        assertThat(result.toString()).doesNotContain(OPERATOR_A.toString()).doesNotContain("Textiles del Norte")
                .doesNotContain("Piura").doesNotContain("Algodón").doesNotContain("commercialData");
    }

    @Test
    void anUnknownOrMalformedQrIsNotFound() throws Exception {
        ResultActions unknown = getAs(OPERATOR_OF_A, "/api/batches/qr/" + "0".repeat(26)).andExpect(status().isNotFound());
        assertThat(code(unknown)).isEqualTo("QR_NOT_FOUND");
        assertThat(read(unknown).path("message").asString()).isEqualTo("Código no encontrado.");
        getAs(OPERATOR_OF_A, "/api/batches/qr/corto").andExpect(status().isNotFound());
        getAs(OPERATOR_OF_A, "/api/batches/qr/UUUUUUUUUUUUUUUUUUUUUUUUUU").andExpect(status().isNotFound());
    }

    @Test
    void theQrAcceptsWhatAPersonTypes() throws Exception {
        JsonNode batch = registerBatch(10);
        String typed = " " + batch.path("qrCode").asString().toLowerCase() + " ";

        getAs(OPERATOR_OF_A, "/api/batches/qr/" + typed.trim()).andExpect(status().isOk())
                .andExpect(jsonPath("$.batch.id").value(batch.path("id").asString()));
    }

    // ---- US13 búsqueda ----

    @Test
    void searchReturnsOnlyTheOwnBatchesNewestFirst() throws Exception {
        clock.advance(Duration.ofSeconds(1));
        JsonNode first = registerBatch(1);
        clock.advance(Duration.ofSeconds(1));
        JsonNode second = registerBatch(2);
        registerBatchAs(OPERATOR_OF_B, 3);

        JsonNode page = read(getAs(OPERATOR_OF_A, "/api/batches").andExpect(status().isOk()));

        assertThat(page.path("items")).hasSize(2);
        assertThat(page.path("items").get(0).path("id").asString()).isEqualTo(second.path("id").asString());
        assertThat(page.path("items").get(1).path("id").asString()).isEqualTo(first.path("id").asString());
        assertThat(page.path("totalElements").asLong()).isEqualTo(2);
        assertThat(page.path("page").asInt()).isZero();
        assertThat(page.path("size").asInt()).isEqualTo(20);
        assertThat(page.path("totalPages").asInt()).isEqualTo(1);
        assertThat(page.path("items").get(0).has("composition")).as("la lista es un resumen").isFalse();
    }

    @Test
    void searchFiltersByTraceabilityIdPrefixNormalizingWhatAPersonTypes() throws Exception {
        JsonNode batch = registerBatch(1);
        registerBatch(2);
        String id = batch.path("traceabilityId").asString();

        assertThat(searchIds("traceabilityId=" + id)).containsExactly(batch.path("id").asString());
        assertThat(searchIds("traceabilityId=" + id.toLowerCase())).containsExactly(batch.path("id").asString());
        assertThat(searchIds("traceabilityId=" + id.substring(0, 6))).contains(batch.path("id").asString());
        assertThat(searchIds("traceabilityId=" + id.substring(3))).isEmpty();
        assertThat(searchIds("traceabilityId=%25")).isEmpty();
        assertThat(searchIds("traceabilityId=TT-U")).isEmpty();
        assertThat(searchIds("traceabilityId=")).hasSize(2);
    }

    @Test
    void searchFiltersByMaterialPhaseAndQuality() throws Exception {
        registerBatch(1);
        var yarn = batchBody(2);
        yarn.put("materialType", "Hilo");
        JsonNode hilo = read(postAs(OPERATOR_OF_A, "/api/batches", yarn).andExpect(status().isCreated()));
        jdbc.sql("UPDATE batches SET current_stage = 'IN_QUALITY_CONTROL', quality_status = 'PENDING_REEVALUATION' WHERE id = :id")
                .param("id", idOf(hilo)).update();

        assertThat(searchIds("materialType=hilo")).containsExactly(hilo.path("id").asString());
        assertThat(searchIds("phase=IN_QUALITY_CONTROL")).containsExactly(hilo.path("id").asString());
        assertThat(searchIds("qualityStatus=PENDING_REEVALUATION")).containsExactly(hilo.path("id").asString());
        assertThat(searchIds("phase=AVAILABLE")).hasSize(1);
        assertThat(searchIds("phase=SPLIT")).isEmpty();
        getAs(OPERATOR_OF_A, "/api/batches?phase=EN_EVALUACION").andExpect(status().isBadRequest());
        getAs(OPERATOR_OF_A, "/api/batches?qualityStatus=NOPE").andExpect(status().isBadRequest());
    }

    @Test
    void searchDatesAreCalendarDaysInLimaBothInclusive() throws Exception {
        // 2026-10-21T03:00:00Z son las 22:00 del 20 de octubre en Lima (UTC−5)
        clock.advance(Duration.between(clock.instant(), Instant.parse("2026-10-21T03:00:00Z")));
        JsonNode batch = registerBatch(1);

        assertThat(searchIds("createdFrom=2026-10-20&createdTo=2026-10-20")).containsExactly(batch.path("id").asString());
        assertThat(searchIds("createdFrom=2026-10-21")).isEmpty();
        assertThat(searchIds("createdTo=2026-10-19")).isEmpty();
        assertThat(searchIds("createdFrom=2026-10-20")).hasSize(1);
        assertThat(searchIds("createdTo=2026-10-20")).hasSize(1);
        assertThat(code(getAs(OPERATOR_OF_A, "/api/batches?createdFrom=2026-10-21&createdTo=2026-10-20").andExpect(status().isBadRequest())))
                .isEqualTo("INVALID_DATE_RANGE");
        getAs(OPERATOR_OF_A, "/api/batches?createdFrom=20-10-2026").andExpect(status().isBadRequest());
    }

    // ---- RNF-03: paginación acotada y orden estable ----

    @Test
    void thePageSizeIsCappedAt100() throws Exception {
        bulkInsertBatches(COMPANY_A, 130, 1);

        JsonNode page = read(getAs(OPERATOR_OF_A, "/api/batches?size=500").andExpect(status().isOk()));

        assertThat(page.path("size").asInt()).as("si piden más de 100 se usa 100").isEqualTo(100);
        assertThat(page.path("items")).hasSize(100);
        assertThat(page.path("totalElements").asLong()).isEqualTo(130);
        assertThat(page.path("totalPages").asInt()).isEqualTo(2);
        JsonNode exact = read(getAs(OPERATOR_OF_A, "/api/batches?size=100&page=1").andExpect(status().isOk()));
        assertThat(exact.path("items")).hasSize(30);
        assertThat(read(getAs(OPERATOR_OF_A, "/api/batches?size=101")).path("size").asInt()).isEqualTo(100);
        assertThat(read(getAs(OPERATOR_OF_A, "/api/batches?size=2147483647")).path("size").asInt()).isEqualTo(100);
    }

    @Test
    void theDefaultPageSizeIs20AndTheSmallerSizesAreRespected() throws Exception {
        bulkInsertBatches(COMPANY_A, 45, 1);

        assertThat(read(getAs(OPERATOR_OF_A, "/api/batches")).path("items")).hasSize(20);
        assertThat(read(getAs(OPERATOR_OF_A, "/api/batches?size=7")).path("items")).hasSize(7);
        assertThat(read(getAs(OPERATOR_OF_A, "/api/batches?size=1")).path("size").asInt()).isEqualTo(1);
        assertThat(read(getAs(OPERATOR_OF_A, "/api/batches?size=20&page=2")).path("items")).hasSize(5);
        assertThat(read(getAs(OPERATOR_OF_A, "/api/batches?page=9")).path("items")).isEmpty();
    }

    @Test
    void anImpossiblePageOrSizeIsA400() throws Exception {
        for (String query : new String[]{"size=0", "size=-3", "page=-1"}) {
            ResultActions result = getAs(OPERATOR_OF_A, "/api/batches?" + query).andExpect(status().isBadRequest());
            assertThat(code(result)).isEqualTo("INVALID_PAGINATION");
        }
        getAs(OPERATOR_OF_A, "/api/batches?size=muchos").andExpect(status().isBadRequest());
    }

    @Test
    void theOrderIsCreatedAtDescendingThenIdSoPagesNeverRepeatOrSkip() throws Exception {
        bulkInsertBatches(COMPANY_A, 60, 1);
        // empates de fecha: 60 lotes en solo 3 instantes distintos
        jdbc.sql("UPDATE batches SET created_at = :start - make_interval(secs => (extract(epoch from :start - created_at))::int / 20)")
                .param("start", java.time.OffsetDateTime.ofInstant(START, java.time.ZoneOffset.UTC)).update();
        List<String> seen = new ArrayList<>();
        for (int page = 0; page < 6; page++) {
            JsonNode items = read(getAs(OPERATOR_OF_A, "/api/batches?size=10&page=" + page)).path("items");
            items.forEach(item -> seen.add(item.path("createdAt").asString() + "|" + item.path("id").asString()));
        }

        assertThat(seen).hasSize(60).doesNotHaveDuplicates();
        List<String> sorted = new ArrayList<>(seen);
        sorted.sort((a, b) -> {
            int byDate = b.split("\\|")[0].compareTo(a.split("\\|")[0]);
            return byDate != 0 ? byDate : a.split("\\|")[1].compareTo(b.split("\\|")[1]);
        });
        assertThat(seen).as("created_at descendente y, a igual fecha, id ascendente").isEqualTo(sorted);
        // pedir dos veces la misma página da lo mismo
        assertThat(read(getAs(OPERATOR_OF_A, "/api/batches?size=10&page=2")).path("items"))
                .isEqualTo(read(getAs(OPERATOR_OF_A, "/api/batches?size=10&page=2")).path("items"));
    }

    private List<String> searchIds(String query) throws Exception {
        List<String> ids = new ArrayList<>();
        read(getAs(OPERATOR_OF_A, "/api/batches?" + query).andExpect(status().isOk())).path("items")
                .forEach(item -> ids.add(item.path("id").asString()));
        return ids;
    }

    // ---- US15 dividir ----

    @Test
    void splittingMarksTheParentAsSplitAndCreatesAvailableChildrenWithNewCodes() throws Exception {
        JsonNode parent = registerBatch(250.5);

        JsonNode result = read(postAs(OPERATOR_OF_A, "/api/batches/" + parent.path("id").asString() + "/split", obj("children", List.of(
                obj("name", "Hijo A", "quantityKg", 100.25), obj("quantityKg", 150.25)))).andExpect(status().isCreated()));

        assertThat(result.path("parent").path("phase").asString()).isEqualTo("SPLIT");
        assertThat(result.path("children")).hasSize(2);
        for (JsonNode child : result.path("children")) {
            assertThat(child.path("phase").asString()).isEqualTo("AVAILABLE");
            assertThat(child.path("originType").asString()).isEqualTo("SPLIT");
            assertThat(child.path("traceabilityId").asString()).isNotEqualTo(parent.path("traceabilityId").asString());
            assertThat(child.path("qrCode").asString()).isNotEqualTo(parent.path("qrCode").asString());
            assertThat(child.path("composition")).isEqualTo(parent.path("composition"));
            assertThat(child.path("materialType").asString()).isEqualTo("Fibra");
            assertThat(child.path("supplierName").asString()).isEqualTo("Textiles del Norte");
        }
        assertThat(result.path("children").get(0).path("name").asString()).isEqualTo("Hijo A");
        assertThat(result.path("children").get(1).path("name").asString()).isEqualTo("Algodón Pima");
        assertThat(count("SELECT count(*) FROM batches")).isEqualTo(3);
        assertThat(getAs(OPERATOR_OF_A, "/api/batches/" + parent.path("id").asString()).andReturn().getResponse().getContentAsString())
                .contains("\"phase\":\"SPLIT\"");
    }

    @Test
    void childrenInheritTheQualityStatusOfTheParent() throws Exception {
        JsonNode parent = registerBatch(100);
        jdbc.sql("UPDATE batches SET quality_status = 'CONFORMING' WHERE id = :id").param("id", idOf(parent)).update();

        JsonNode result = read(postAs(OPERATOR_OF_A, "/api/batches/" + parent.path("id").asString() + "/split", obj("children", List.of(
                obj("quantityKg", 40), obj("quantityKg", 60)))).andExpect(status().isCreated()));

        result.path("children").forEach(child -> assertThat(child.path("qualityStatus").asString()).isEqualTo("CONFORMING"));
    }

    @Test
    void childrenThatDoNotAddUpAreRejectedAndNothingChanges() throws Exception {
        JsonNode parent = registerBatch(100);

        ResultActions result = postAs(OPERATOR_OF_A, "/api/batches/" + parent.path("id").asString() + "/split", obj("children", List.of(
                obj("quantityKg", 40), obj("quantityKg", 59.999)))).andExpect(status().isBadRequest());

        assertThat(code(result)).isEqualTo("SPLIT_QUANTITIES_MISMATCH");
        assertThat(read(result).path("message").asString()).contains("cantidades no coinciden");
        assertThat(count("SELECT count(*) FROM batches")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM batches WHERE current_stage = 'SPLIT'")).isZero();
    }

    @Test
    void aSplitNeedsTwoOrMoreChildren() throws Exception {
        JsonNode parent = registerBatch(100);
        String url = "/api/batches/" + parent.path("id").asString() + "/split";

        assertThat(code(postAs(OPERATOR_OF_A, url, obj("children", List.of(obj("quantityKg", 100)))).andExpect(status().isBadRequest())))
                .isEqualTo("SPLIT_REQUIRES_TWO_CHILDREN");
        assertThat(code(postAs(OPERATOR_OF_A, url, obj("children", List.of())).andExpect(status().isBadRequest()))).isEqualTo("SPLIT_REQUIRES_TWO_CHILDREN");
        postAs(OPERATOR_OF_A, url, obj("children", List.of(obj("quantityKg", 50), obj("name", "sin cantidad")))).andExpect(status().isBadRequest());
        postAs(OPERATOR_OF_A, url, obj()).andExpect(status().isBadRequest());
    }

    @Test
    void aBatchThatIsNotEnabledCannotBeSplit() throws Exception {
        JsonNode held = registerBatch(100);
        jdbc.sql("UPDATE batches SET quality_hold = TRUE WHERE id = :id").param("id", idOf(held)).update();
        JsonNode inQuality = registerBatch(100);
        jdbc.sql("UPDATE batches SET current_stage = 'IN_QUALITY_CONTROL' WHERE id = :id").param("id", idOf(inQuality)).update();
        var children = obj("children", List.of(obj("quantityKg", 40), obj("quantityKg", 60)));

        assertThat(code(postAs(OPERATOR_OF_A, "/api/batches/" + held.path("id").asString() + "/split", children).andExpect(status().isConflict())))
                .isEqualTo("BATCH_ON_QUALITY_HOLD");
        assertThat(code(postAs(OPERATOR_OF_A, "/api/batches/" + inQuality.path("id").asString() + "/split", children).andExpect(status().isConflict())))
                .isEqualTo("INVALID_PHASE_TRANSITION");
        assertThat(count("SELECT count(*) FROM batches")).isEqualTo(2);
    }

    @Test
    void aSplitCannotBeRepeatedAndAnotherCompanyCannotSplit() throws Exception {
        JsonNode parent = registerBatch(100);
        String url = "/api/batches/" + parent.path("id").asString() + "/split";
        var children = obj("children", List.of(obj("quantityKg", 40), obj("quantityKg", 60)));

        postAs(OPERATOR_OF_B, url, children).andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM batches")).isEqualTo(1);
        postAs(OPERATOR_OF_A, url, children).andExpect(status().isCreated());
        assertThat(code(postAs(OPERATOR_OF_A, url, children).andExpect(status().isConflict()))).isEqualTo("INVALID_PHASE_TRANSITION");
        assertThat(count("SELECT count(*) FROM batches")).isEqualTo(3);
    }

    @Test
    void commercialDataOnlyAppliesToAFinalProduct() throws Exception {
        JsonNode batch = registerBatch(10);

        ResultActions result = call(OPERATOR_OF_A, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/batches/" + batch.path("id").asString() + "/commercial-data").contentType("application/json")
                .content(json.writeValueAsString(obj("buyerOrDistributor", "Tiendas SAC")))).andExpect(status().isConflict());

        assertThat(code(result)).isEqualTo("NOT_A_FINAL_PRODUCT");
    }

    @Test
    void anOperationRolledBackLeavesNoBatchBehind() throws Exception {
        postAs(OPERATOR_OF_A, "/api/batches", obj("materialType", "Fibra")).andExpect(status().isBadRequest());
        assertThat(count("SELECT count(*) FROM batches")).isZero();
        assertThat(count("SELECT count(*) FROM audit_log")).isZero();
        assertThat(count("SELECT count(*) FROM outbox")).as("un registro fallido no deja eventos en el outbox").isZero();
    }

    /** M6: el nombre del lote admite hasta 20 caracteres (en el registro, en la división y en las salidas); más es 400 {@code INVALID_BATCH_NAME}. */
    @Test
    void aBatchNameOfTwentyCharactersIsAcceptedAndTwentyOneIsInvalidBatchName() throws Exception {
        Map<String, Object> ok = new java.util.HashMap<>(batchBody(10));
        ok.put("name", "A".repeat(20));
        postAs(OPERATOR_OF_A, "/api/batches", ok).andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("A".repeat(20)));

        Map<String, Object> tooLong = new java.util.HashMap<>(batchBody(10));
        tooLong.put("name", "A".repeat(21));
        ResultActions result = postAs(OPERATOR_OF_A, "/api/batches", tooLong).andExpect(status().isBadRequest());
        assertThat(code(result)).isEqualTo("INVALID_BATCH_NAME");
        assertThat(read(result).path("details").get(0).asString()).isEqualTo("name");
        assertThat(count("SELECT count(*) FROM batches")).as("el rechazado no se guarda").isEqualTo(1);

        UUID parent = idOf(registerBatch(20));
        ResultActions split = postAs(OPERATOR_OF_A, "/api/batches/" + parent + "/split",
                obj("children", List.of(obj("name", "B".repeat(21), "quantityKg", 10), obj("quantityKg", 10)))).andExpect(status().isBadRequest());
        assertThat(code(split)).isEqualTo("INVALID_BATCH_NAME");
    }
}
