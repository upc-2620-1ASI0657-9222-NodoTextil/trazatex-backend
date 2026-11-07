package com.nodotextil.trazatex.production.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La regla de docs/architecture/communication.md: {@code BatchPhaseChanged} por cada lote cuya fase o retención por calidad
 * cambie, sea cual sea la operación, además del evento de negocio. Recorre <b>todas</b> las operaciones de escritura de
 * Production: la lista se saca del mapeo real de Spring y la prueba falla si una ruta no está clasificada (una operación nueva
 * no puede olvidar el evento) o si una clasificada ya no existe.
 */
class BatchPhaseChangedEventsIT extends ApiTestBase {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    // ---- lo esperado de cada operación ----

    private record Business(String type, String aggregateId, String topic) {
    }

    private record Phase(UUID batchId, UUID companyId, String previous, String next, boolean hold, String reason, String requestId) {
    }

    /** Lo que la operación debe dejar en el outbox, en orden: primero los eventos de negocio, luego un BatchPhaseChanged por lote. */
    private record Outcome(List<Business> business, List<Phase> phases) {
        static Outcome of(List<Business> business, List<Phase> phases) {
            List<Phase> sorted = new ArrayList<>(phases);
            sorted.sort(Comparator.comparing(phase -> phase.batchId().toString()));
            return new Outcome(business, sorted);
        }
    }

    @FunctionalInterface
    private interface Scenario {
        Outcome run() throws Exception;
    }

    private static final String BATCH = "production.batch";

    /** ruta → ¿cambia fase o retención? Es la clasificación que la prueba de completitud exige. */
    private static final Map<String, Boolean> CHANGES_PHASE = new LinkedHashMap<>();

    static {
        CHANGES_PHASE.put("POST /api/batches", false);
        CHANGES_PHASE.put("POST /api/batches/{batchId}/split", true);
        CHANGES_PHASE.put("PUT /api/batches/{id}/commercial-data", false);
        CHANGES_PHASE.put("POST /api/batches/{id}/evidence", false);
        CHANGES_PHASE.put("POST /api/transformations/{id}/evidence", false);
        // Borrado de evidencias (agregado en V2): marca la evidencia como borrada; no toca fase ni retención del lote
        CHANGES_PHASE.put("DELETE /api/batches/{id}/evidence/{evidenceId}", false);
        CHANGES_PHASE.put("DELETE /api/transformations/{id}/evidence/{evidenceId}", false);
        CHANGES_PHASE.put("POST /api/machines", false);
        CHANGES_PHASE.put("PATCH /api/machines/{id}/status", false);
        CHANGES_PHASE.put("POST /api/transformations", true);
        CHANGES_PHASE.put("POST /api/transformations/{id}/complete", true);
        CHANGES_PHASE.put("POST /api/transfers", true);
        CHANGES_PHASE.put("POST /api/transfers/{id}/accept", true);
        CHANGES_PHASE.put("POST /api/transfers/{id}/reject", true);
        // Comando interno de Quality (04e): cambia fase o retención. Sus cinco operaciones tienen escenario aparte ("ruta#OPERACIÓN").
        CHANGES_PHASE.put("POST /internal/batches/{batchId}/phase-transitions", true);
    }

    private Map<String, Scenario> scenarios() {
        Map<String, Scenario> scenarios = new LinkedHashMap<>();
        scenarios.put("POST /api/batches", () -> {
            JsonNode batch = registerBatch(10);
            return Outcome.of(List.of(new Business("BatchRegistered", idOf(batch).toString(), BATCH)), List.of());
        });
        scenarios.put("POST /api/batches/{batchId}/split", () -> {
            JsonNode parent = registerBatch(10);
            clearOutbox();
            postAs(OPERATOR_OF_A, "/api/batches/" + idOf(parent) + "/split",
                    obj("children", List.of(obj("quantityKg", 4), obj("quantityKg", 6)))).andExpect(status().isCreated());
            return Outcome.of(List.of(new Business("BatchSplit", idOf(parent).toString(), BATCH)),
                    List.of(phase(parent, COMPANY_A, "AVAILABLE", "SPLIT", false, "SPLIT", idOf(parent))));
        });
        scenarios.put("PUT /api/batches/{id}/commercial-data", () -> {
            JsonNode finalProduct = completedFinalProduct();
            clearOutbox();
            putAs(OPERATOR_OF_A, "/api/batches/" + idOf(finalProduct) + "/commercial-data", obj("buyerOrDistributor", "Tiendas SAC"))
                    .andExpect(status().isOk());
            return Outcome.of(List.of(), List.of());
        });
        scenarios.put("POST /api/batches/{id}/evidence", () -> {
            JsonNode batch = registerBatch(10);
            clearOutbox();
            multipartAs(OPERATOR_OF_A, "/api/batches/" + idOf(batch) + "/evidence",
                    new org.springframework.mock.web.MockMultipartFile("files", "a.jpg", "image/jpeg", com.nodotextil.trazatex.production.domain.SampleImages.jpeg()))
                    .andExpect(status().isCreated());
            return Outcome.of(List.of(), List.of());
        });
        scenarios.put("POST /api/transformations/{id}/evidence", () -> {
            JsonNode input = registerBatch(10);
            UUID transformation = idOf(startTransformation("SPINNING", idOf(registerMachine("MQ-EV")), idOf(input)));
            clearOutbox();
            multipartAs(OPERATOR_OF_A, "/api/transformations/" + transformation + "/evidence",
                    new org.springframework.mock.web.MockMultipartFile("files", "a.png", "image/png", com.nodotextil.trazatex.production.domain.SampleImages.png()))
                    .andExpect(status().isCreated());
            return Outcome.of(List.of(), List.of());
        });
        scenarios.put("DELETE /api/batches/{id}/evidence/{evidenceId}", () -> {
            JsonNode batch = registerBatch(10);
            String evidence = read(multipartAs(OPERATOR_OF_A, "/api/batches/" + idOf(batch) + "/evidence",
                    new org.springframework.mock.web.MockMultipartFile("files", "a.jpg", "image/jpeg", com.nodotextil.trazatex.production.domain.SampleImages.jpeg()))
                    .andExpect(status().isCreated())).path("items").get(0).path("id").asString();
            clearOutbox();
            call(OPERATOR_OF_A, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/batches/" + idOf(batch) + "/evidence/" + evidence)).andExpect(status().isNoContent());
            return Outcome.of(List.of(), List.of());
        });
        scenarios.put("DELETE /api/transformations/{id}/evidence/{evidenceId}", () -> {
            JsonNode input = registerBatch(10);
            UUID transformation = idOf(startTransformation("SPINNING", idOf(registerMachine("MQ-EVD")), idOf(input)));
            String evidence = read(multipartAs(OPERATOR_OF_A, "/api/transformations/" + transformation + "/evidence",
                    new org.springframework.mock.web.MockMultipartFile("files", "a.png", "image/png", com.nodotextil.trazatex.production.domain.SampleImages.png()))
                    .andExpect(status().isCreated())).path("items").get(0).path("id").asString();
            clearOutbox();
            call(OPERATOR_OF_A, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/transformations/" + transformation + "/evidence/" + evidence)).andExpect(status().isNoContent());
            return Outcome.of(List.of(), List.of());
        });
        scenarios.put("POST /api/machines", () -> {
            registerMachine("MQ-1");
            return Outcome.of(List.of(), List.of());
        });
        scenarios.put("PATCH /api/machines/{id}/status", () -> {
            UUID machine = idOf(registerMachine("MQ-2"));
            clearOutbox();
            patchAs(ADMIN_OF_A, "/api/machines/" + machine + "/status", obj("status", "OUT_OF_SERVICE")).andExpect(status().isOk());
            return Outcome.of(List.of(), List.of());
        });
        scenarios.put("POST /api/transformations", () -> {
            JsonNode a = registerBatch(10);
            JsonNode b = registerBatch(20);
            UUID machine = idOf(registerMachine("MQ-3"));
            clearOutbox();
            UUID transformation = idOf(startTransformation("SPINNING", machine, idOf(a), idOf(b)));
            return Outcome.of(List.of(), List.of(
                    phase(a, COMPANY_A, "AVAILABLE", "IN_TRANSFORMATION", false, "TRANSFORMATION_STARTED", transformation),
                    phase(b, COMPANY_A, "AVAILABLE", "IN_TRANSFORMATION", false, "TRANSFORMATION_STARTED", transformation)));
        });
        scenarios.put("POST /api/transformations/{id}/complete", () -> {
            JsonNode a = registerBatch(10);
            JsonNode b = registerBatch(20);
            UUID transformation = idOf(startTransformation("SPINNING", idOf(registerMachine("MQ-4")), idOf(a), idOf(b)));
            clearOutbox();
            postAs(OPERATOR_OF_A, "/api/transformations/" + transformation + "/complete",
                    obj("outputs", List.of(obj("materialType", "Hilo", "quantityKg", 25)), "wasteKg", 2, "wasteReason", "Orillo"))
                    .andExpect(status().isOk());
            return Outcome.of(List.of(new Business("TransformationCompleted", transformation.toString(), "production.transformation")),
                    List.of(phase(a, COMPANY_A, "IN_TRANSFORMATION", "PROCESSED", false, "TRANSFORMATION_COMPLETED", transformation),
                            phase(b, COMPANY_A, "IN_TRANSFORMATION", "PROCESSED", false, "TRANSFORMATION_COMPLETED", transformation)));
        });
        scenarios.put("POST /api/transfers", () -> {
            JsonNode a = registerBatch(10);
            JsonNode b = registerBatch(20);
            clearOutbox();
            UUID transfer = idOf(read(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B,
                    "batchIds", List.of(idOf(a), idOf(b)))).andExpect(status().isCreated())));
            return Outcome.of(List.of(new Business("TransferInitiated", transfer.toString(), "production.transfer")), List.of(
                    phase(a, COMPANY_A, "AVAILABLE", "IN_TRANSFER", false, "TRANSFER_INITIATED", transfer),
                    phase(b, COMPANY_A, "AVAILABLE", "IN_TRANSFER", false, "TRANSFER_INITIATED", transfer)));
        });
        scenarios.put("POST /api/transfers/{id}/accept", () -> {
            JsonNode a = registerBatch(10);
            JsonNode b = registerBatch(20);
            UUID transfer = idOf(read(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B,
                    "batchIds", List.of(idOf(a), idOf(b)))).andExpect(status().isCreated())));
            clearOutbox();
            postAs(ADMIN_OF_B, "/api/transfers/" + transfer + "/accept").andExpect(status().isOk());
            return Outcome.of(List.of(new Business("TransferAccepted", transfer.toString(), "production.transfer")), List.of(
                    phase(a, COMPANY_B, "IN_TRANSFER", "AVAILABLE", false, "TRANSFER_ACCEPTED", transfer),
                    phase(b, COMPANY_B, "IN_TRANSFER", "AVAILABLE", false, "TRANSFER_ACCEPTED", transfer)));
        });
        scenarios.put("POST /api/transfers/{id}/reject", () -> {
            JsonNode a = registerBatch(10);
            JsonNode b = registerBatch(20);
            UUID transfer = idOf(read(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B,
                    "batchIds", List.of(idOf(a), idOf(b)))).andExpect(status().isCreated())));
            clearOutbox();
            postAs(ADMIN_OF_B, "/api/transfers/" + transfer + "/reject", obj("reason", "Material dañado")).andExpect(status().isOk());
            return Outcome.of(List.of(new Business("TransferRejected", transfer.toString(), "production.transfer")), List.of(
                    phase(a, COMPANY_A, "IN_TRANSFER", "AVAILABLE", false, "TRANSFER_REJECTED", transfer),
                    phase(b, COMPANY_A, "IN_TRANSFER", "AVAILABLE", false, "TRANSFER_REJECTED", transfer)));
        });
        String internal = "POST /internal/batches/{batchId}/phase-transitions";
        scenarios.put(internal + "#START_EVALUATION", () -> {
            JsonNode batch = registerBatch(10);
            clearOutbox();
            internalCommand(idOf(batch), phaseCommand("START_EVALUATION", "ctl-1")).andExpect(status().isOk());
            return Outcome.of(List.of(), List.of(phaseOfCommand(batch, "AVAILABLE", "IN_QUALITY_CONTROL", false, "START_EVALUATION", "ctl-1")));
        });
        scenarios.put(internal + "#RELEASE_CONFORMING", () -> {
            JsonNode batch = inQualityControl();
            clearOutbox();
            internalCommand(idOf(batch), phaseCommand("RELEASE", "ctl-1", false, "Control conforme")).andExpect(status().isOk());
            return Outcome.of(List.of(), List.of(phaseOfCommand(batch, "IN_QUALITY_CONTROL", "AVAILABLE", false, "Control conforme", "ctl-1")));
        });
        scenarios.put(internal + "#RELEASE_WITH_FAILURE", () -> {
            JsonNode batch = inQualityControl();
            clearOutbox();
            internalCommand(idOf(batch), phaseCommand("RELEASE", "ctl-1", true, "Control con falla")).andExpect(status().isOk());
            return Outcome.of(List.of(), List.of(phaseOfCommand(batch, "IN_QUALITY_CONTROL", "AVAILABLE", true, "Control con falla", "ctl-1")));
        });
        scenarios.put(internal + "#DISCARD", () -> {
            JsonNode batch = registerBatch(10);
            clearOutbox();
            internalCommand(idOf(batch), phaseCommand("DISCARD", "fail-1")).andExpect(status().isOk());
            return Outcome.of(List.of(), List.of(phaseOfCommand(batch, "AVAILABLE", "DISCARDED", false, "DISCARD", "fail-1")));
        });
        scenarios.put(internal + "#HOLD", () -> {
            JsonNode batch = registerBatch(10);
            clearOutbox();
            internalCommand(idOf(batch), obj("operation", "HOLD", "requestId", "derived-1", "reason", "Posible falla derivada"))
                    .andExpect(status().isOk());
            return Outcome.of(List.of(), List.of(phaseOfCommand(batch, "AVAILABLE", "AVAILABLE", true, "Posible falla derivada", "derived-1")));
        });
        scenarios.put(internal + "#RELEASE_HOLD", () -> {
            JsonNode batch = registerBatch(10);
            internalCommand(idOf(batch), phaseCommand("HOLD", "derived-1")).andExpect(status().isOk());
            clearOutbox();
            internalCommand(idOf(batch), phaseCommand("RELEASE_HOLD", "fail-1")).andExpect(status().isOk());
            return Outcome.of(List.of(), List.of(phaseOfCommand(batch, "AVAILABLE", "AVAILABLE", false, "RELEASE_HOLD", "fail-1")));
        });
        // Sin cambio no hay evento: HOLD sobre un lote ya retenido, RELEASE_HOLD sobre uno libre y la repetición de un comando
        scenarios.put(internal + "#HOLD_WITHOUT_CHANGE", () -> {
            JsonNode batch = registerBatch(10);
            internalCommand(idOf(batch), phaseCommand("HOLD", "derived-1")).andExpect(status().isOk());
            clearOutbox();
            internalCommand(idOf(batch), phaseCommand("HOLD", "derived-2")).andExpect(status().isOk());
            return Outcome.of(List.of(), List.of());
        });
        scenarios.put(internal + "#RELEASE_HOLD_WITHOUT_CHANGE", () -> {
            JsonNode batch = registerBatch(10);
            clearOutbox();
            internalCommand(idOf(batch), phaseCommand("RELEASE_HOLD", "fail-1")).andExpect(status().isOk());
            return Outcome.of(List.of(), List.of());
        });
        scenarios.put(internal + "#REPEATED_COMMAND", () -> {
            JsonNode batch = registerBatch(10);
            internalCommand(idOf(batch), phaseCommand("START_EVALUATION", "ctl-1")).andExpect(status().isOk());
            clearOutbox();
            internalCommand(idOf(batch), phaseCommand("START_EVALUATION", "ctl-1")).andExpect(status().isOk());
            return Outcome.of(List.of(), List.of());
        });
        return scenarios;
    }

    private JsonNode inQualityControl() throws Exception {
        JsonNode batch = registerBatch(10);
        internalCommand(idOf(batch), phaseCommand("START_EVALUATION", "ctl-1")).andExpect(status().isOk());
        return batch;
    }

    /** El BatchPhaseChanged de un comando interno: la empresa es la del lote y requestId/reason vienen del comando. */
    private static Phase phaseOfCommand(JsonNode batch, String previous, String next, boolean hold, String reason, String requestId) {
        return new Phase(UUID.fromString(batch.path("id").asString()), COMPANY_A, previous, next, hold, reason, requestId);
    }

    // ---- la prueba que recorre todas las operaciones ----

    @Test
    void everyWriteRouteIsClassifiedAndHasAScenario() {
        Set<String> actual = writeRoutes();

        assertThat(actual)
                .as("Hay una ruta de escritura sin clasificar (o una clasificada que ya no existe): decida si cambia fase o "
                        + "retención del lote, agréguela a CHANGES_PHASE y escriba su escenario")
                .isEqualTo(new TreeSet<>(CHANGES_PHASE.keySet()));
        assertThat(scenarios().keySet().stream().map(BatchPhaseChangedEventsIT::routeOf).collect(java.util.stream.Collectors.toSet()))
                .as("cada ruta clasificada tiene al menos un escenario, y ningún escenario es de una ruta sin clasificar")
                .isEqualTo(CHANGES_PHASE.keySet());
    }

    /** "ruta#OPERACIÓN" → "ruta": una ruta con varias operaciones (el comando interno) tiene un escenario por cada una. */
    private static String routeOf(String scenario) {
        int hash = scenario.indexOf('#');
        return hash < 0 ? scenario : scenario.substring(0, hash);
    }

    @Test
    void everyWriteOperationPublishesExactlyTheBatchPhaseChangedEventsOfTheBatchesItChanged() throws Exception {
        Map<String, Boolean> publishedByRoute = new LinkedHashMap<>();
        for (Map.Entry<String, Scenario> entry : scenarios().entrySet()) {
            String route = entry.getKey();
            clearOutbox();

            Outcome expected = entry.getValue().run();

            List<Event> events = outbox();
            List<Event> phaseEvents = events.stream().filter(e -> e.type().equals("BatchPhaseChanged")).toList();
            assertThat(phaseEvents.isEmpty()).as(route + ": publica BatchPhaseChanged exactamente cuando algo cambió").isEqualTo(expected.phases().isEmpty());
            publishedByRoute.merge(routeOf(route), !phaseEvents.isEmpty(), Boolean::logicalOr);
            assertThat(events.stream().map(e -> e.type() + "|" + e.aggregateId() + "|" + e.topic()).toList())
                    .as(route + ": eventos en orden (negocio primero, luego un BatchPhaseChanged por lote)")
                    .isEqualTo(java.util.stream.Stream.concat(
                            expected.business().stream().map(b -> b.type() + "|" + b.aggregateId() + "|" + b.topic()),
                            expected.phases().stream().map(p -> "BatchPhaseChanged|" + p.batchId() + "|" + BATCH)).toList());
            for (int i = 0; i < expected.phases().size(); i++) {
                Phase want = expected.phases().get(i);
                JsonNode payload = phaseEvents.get(i).payload().path("payload");
                assertThat(payload.path("batchId").asString()).as(route).isEqualTo(want.batchId().toString());
                assertThat(payload.path("companyId").asString()).as(route + " companyId").isEqualTo(want.companyId().toString());
                assertThat(payload.path("previousPhase").asString()).as(route + " previousPhase").isEqualTo(want.previous());
                assertThat(payload.path("newPhase").asString()).as(route + " newPhase").isEqualTo(want.next());
                assertThat(payload.path("qualityHold").asBoolean()).as(route + " qualityHold").isEqualTo(want.hold());
                assertThat(payload.path("reason").asString()).as(route + " reason").isEqualTo(want.reason());
                assertThat(payload.path("requestId").asString()).as(route + " requestId").isEqualTo(want.requestId());
            }
            assertThat(payload(phaseEvents)).as(route + ": solo los campos del catálogo")
                    .allSatisfy(names -> assertThat(names).containsExactlyInAnyOrder("batchId", "companyId", "previousPhase",
                            "newPhase", "qualityHold", "reason", "requestId", "batchVersion"));
        }
        // Clasificada como "cambia fase o retención" ⇒ al menos un escenario de la ruta publica; "no cambia" ⇒ ninguno
        assertThat(publishedByRoute).as("la clasificación de cada ruta coincide con lo que publica").isEqualTo(CHANGES_PHASE);
    }

    // ---- bordes de la regla ----

    @Test
    void anOperationThatFailsLeavesNoEventBehind() throws Exception {
        JsonNode free = registerBatch(10);
        JsonNode held = registerBatch(10);
        jdbc.sql("UPDATE batches SET quality_hold = TRUE WHERE id = :id").param("id", idOf(held)).update();
        UUID machine = idOf(registerMachine("MQ-5"));
        clearOutbox();

        postAs(OPERATOR_OF_A, "/api/transformations", obj("type", "SPINNING", "machineId", machine,
                "batchIds", List.of(idOf(free), idOf(held)))).andExpect(status().isConflict());
        postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(free), idOf(held))))
                .andExpect(status().isConflict());
        postAs(OPERATOR_OF_A, "/api/batches/" + idOf(free) + "/split", obj("children", List.of(obj("quantityKg", 1))))
                .andExpect(status().isBadRequest());

        assertThat(outbox()).isEmpty();
        assertThat(count("SELECT count(*) FROM batches WHERE current_stage <> 'AVAILABLE'")).isZero();
    }

    @Test
    void repeatingARequestWithTheSameIdempotencyKeyPublishesNothingNew() throws Exception {
        JsonNode a = registerBatch(10);
        JsonNode b = registerBatch(20);
        clearOutbox();
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(a), idOf(b)));

        postWithKey(OPERATOR_OF_A, "/api/transfers", body, key).andExpect(status().isCreated());
        List<Event> afterFirst = outbox();
        postWithKey(OPERATOR_OF_A, "/api/transfers", body, key).andExpect(status().isCreated());

        assertThat(afterFirst).hasSize(3);
        assertThat(outbox()).extracting(Event::id).containsExactlyElementsOf(afterFirst.stream().map(Event::id).toList());
    }

    @Test
    void readingAndChangingOnlyTheDisplayedDataPublishNothing() throws Exception {
        JsonNode finalProduct = completedFinalProduct();
        clearOutbox();

        getAs(OPERATOR_OF_A, "/api/batches").andExpect(status().isOk());
        getAs(OPERATOR_OF_A, "/api/batches/" + idOf(finalProduct)).andExpect(status().isOk());
        getAs(ADMIN_OF_A, "/api/machines").andExpect(status().isOk());
        putAs(OPERATOR_OF_A, "/api/batches/" + idOf(finalProduct) + "/commercial-data", obj("buyerOrDistributor", "Otra SAC"))
                .andExpect(status().isOk());

        assertThat(outbox()).isEmpty();
    }

    @Test
    void theEventsOfOneOperationAreWrittenInOrderWithTheirOwnTimestamps() throws Exception {
        JsonNode a = registerBatch(10);
        JsonNode b = registerBatch(20);
        JsonNode c = registerBatch(30);
        clearOutbox();

        postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(a), idOf(b), idOf(c))))
                .andExpect(status().isCreated());

        List<java.time.OffsetDateTime> createdAt = jdbc.sql("SELECT created_at FROM outbox ORDER BY created_at")
                .query(java.time.OffsetDateTime.class).list();
        assertThat(createdAt).hasSize(4).doesNotHaveDuplicates().isSorted();
        assertThat(outbox().get(0).type()).isEqualTo("TransferInitiated");
    }

    // ---- contenido de los eventos de negocio ----

    @Test
    void batchRegisteredCarriesTheCatalogFieldsAndNothingPrivate() throws Exception {
        JsonNode batch = registerBatch(12.5);

        Event event = outbox().get(0);

        assertThat(event.type()).isEqualTo("BatchRegistered");
        assertThat(event.payload().propertyNames()).containsExactlyInAnyOrder("eventId", "type", "aggregateId", "version", "occurredAt", "payload");
        assertThat(event.payload().path("eventId").asString()).isEqualTo(event.id().toString());
        assertThat(event.payload().path("aggregateId").asString()).isEqualTo(idOf(batch).toString());
        JsonNode payload = event.payload().path("payload");
        assertThat(payload.propertyNames()).containsExactlyInAnyOrder("batchId", "traceabilityId", "qrCode", "companyId", "materialType",
                "composition", "quantityKg", "origin", "supplierName", "receptionCharacteristics", "createdAt", "name");
        // name: el nombre libre del lote (solo para la empresa dueña; Traceability lo filtra, ver communication.md)
        assertThat(payload.path("name").asString()).isEqualTo("Algodón Pima").isEqualTo(batch.path("name").asString());
        assertThat(payload.path("traceabilityId").asString()).isEqualTo(batch.path("traceabilityId").asString());
        assertThat(payload.path("qrCode").asString()).isEqualTo(batch.path("qrCode").asString());
        assertThat(payload.path("companyId").asString()).isEqualTo(COMPANY_A.toString());
        assertThat(payload.path("quantityKg").decimalValue()).isEqualByComparingTo("12.5");
        assertThat(payload.path("composition")).hasSize(2);
        assertThat(payload.path("composition").get(0).propertyNames()).containsExactlyInAnyOrder("name", "percentage");
        assertThat(payload.path("origin").asString()).isEqualTo("Piura");
        assertThat(payload.path("supplierName").asString()).isEqualTo("Textiles del Norte");
    }

    @Test
    void batchSplitIntroducesTheChildrenWithTheirCodesAndTheParentsQualityStatus() throws Exception {
        JsonNode parent = registerBatch(10);
        clearOutbox();

        JsonNode split = read(postAs(OPERATOR_OF_A, "/api/batches/" + idOf(parent) + "/split",
                obj("children", List.of(obj("quantityKg", 4), obj("quantityKg", 6)))).andExpect(status().isCreated()));

        JsonNode payload = outbox().get(0).payload().path("payload");
        assertThat(payload.propertyNames()).containsExactlyInAnyOrder("parentBatchId", "companyId", "children");
        assertThat(payload.path("parentBatchId").asString()).isEqualTo(idOf(parent).toString());
        assertThat(payload.path("children")).hasSize(2);
        for (int i = 0; i < 2; i++) {
            JsonNode child = payload.path("children").get(i);
            JsonNode created = split.path("children").get(i);
            assertThat(child.propertyNames()).containsExactlyInAnyOrder("batchId", "traceabilityId", "qrCode", "materialType", "quantityKg",
                    "composition", "origin", "supplierName", "receptionCharacteristics", "qualityStatus", "createdAt", "name");
            // sin nombre en la petición, el hijo hereda el del padre
            assertThat(child.path("name").asString()).isEqualTo(created.path("name").asString()).isEqualTo(parent.path("name").asString());
            // heredados del padre: sin ellos los consumidores no conocerían el material ni la composición de un hijo
            assertThat(child.path("materialType").asString()).isEqualTo(parent.path("materialType").asString());
            assertThat(child.path("composition")).isEqualTo(parent.path("composition"));
            assertThat(child.path("origin").asString()).isEqualTo("Piura");
            assertThat(child.path("supplierName").asString()).isEqualTo("Textiles del Norte");
            assertThat(child.path("receptionCharacteristics").asString()).isEqualTo(parent.path("receptionCharacteristics").asString());
            assertThat(java.time.Instant.parse(child.path("createdAt").asString()))
                    .isEqualTo(java.time.Instant.parse(created.path("createdAt").asString()));
            assertThat(child.path("batchId").asString()).isEqualTo(created.path("id").asString());
            assertThat(child.path("traceabilityId").asString()).isEqualTo(created.path("traceabilityId").asString());
            assertThat(child.path("qualityStatus").asString()).isEqualTo("NOT_REVIEWED");
        }
        assertThat(payload.path("children").get(0).path("quantityKg").decimalValue()).isEqualByComparingTo("4");
    }

    @Test
    void theChildrenOfAnOutputCarryNoOriginSupplierOrReceptionBecauseTheOutputHasNone() throws Exception {
        JsonNode input = registerBatch(100);
        UUID transformation = idOf(startTransformation("SPINNING", idOf(registerMachine("MQ-7")), idOf(input)));
        JsonNode output = read(postAs(OPERATOR_OF_A, "/api/transformations/" + transformation + "/complete",
                obj("outputs", List.of(obj("materialType", "Hilo", "quantityKg", 90)))).andExpect(status().isOk())).path("outputs").get(0);
        clearOutbox();

        postAs(OPERATOR_OF_A, "/api/batches/" + idOf(output) + "/split",
                obj("children", List.of(obj("quantityKg", 40), obj("quantityKg", 50)))).andExpect(status().isCreated());

        JsonNode children = outbox().get(0).payload().path("payload").path("children");
        assertThat(children).hasSize(2);
        for (JsonNode child : children) {
            assertThat(child.path("materialType").asString()).isEqualTo("Hilo");
            assertThat(child.path("composition")).isNotEmpty();
            for (String field : List.of("origin", "supplierName", "receptionCharacteristics")) {
                assertThat(child.path(field).isNull() || child.path(field).isMissingNode()).as(field).isTrue();
            }
        }
    }

    @Test
    void aGrandchildInheritsTheSameDataThroughTheChain() throws Exception {
        JsonNode parent = registerBatch(100);
        JsonNode child = read(postAs(OPERATOR_OF_A, "/api/batches/" + idOf(parent) + "/split",
                obj("children", List.of(obj("quantityKg", 40), obj("quantityKg", 60)))).andExpect(status().isCreated())).path("children").get(0);
        clearOutbox();

        postAs(OPERATOR_OF_A, "/api/batches/" + idOf(child) + "/split",
                obj("children", List.of(obj("quantityKg", 10), obj("quantityKg", 30)))).andExpect(status().isCreated());

        for (JsonNode grandchild : outbox().get(0).payload().path("payload").path("children")) {
            assertThat(grandchild.path("materialType").asString()).isEqualTo(parent.path("materialType").asString());
            assertThat(grandchild.path("origin").asString()).isEqualTo("Piura");
            assertThat(grandchild.path("supplierName").asString()).isEqualTo("Textiles del Norte");
            assertThat(grandchild.path("composition")).isEqualTo(parent.path("composition"));
        }
    }

    @Test
    void transformationCompletedListsInputsAndOutputsAndNeverTheCommercialData() throws Exception {
        JsonNode input = registerBatch(100);
        UUID machine = idOf(registerMachine("MQ-6"));
        UUID transformation = idOf(startTransformation("DYEING", machine, idOf(input)));
        clearOutbox();

        JsonNode completed = read(postAs(OPERATOR_OF_A, "/api/transformations/" + transformation + "/complete", obj("outputs", List.of(
                obj("materialType", "Tela", "quantityKg", 80),
                obj("materialType", "Tela teñida", "quantityKg", 10, "finalProduct", true,
                        "commercialData", obj("buyerOrDistributor", "Tiendas SAC", "salePrice", 99.9, "currency", "USD"))),
                "wasteKg", 5, "wasteReason", "Orillo")).andExpect(status().isOk()));

        Event event = outbox().stream().filter(e -> e.type().equals("TransformationCompleted")).findFirst().orElseThrow();
        JsonNode payload = event.payload().path("payload");
        assertThat(payload.propertyNames()).containsExactlyInAnyOrder("transformationId", "type", "companyId", "machineId", "machineName",
                "machineType", "inputs", "outputs", "wasteKg", "wasteReason", "shrinkageKg", "startedAt", "completedAt");
        assertThat(payload.path("machineName").asString()).as("MACHINERY: lo que otra empresa puede leer").isEqualTo("Cardadora");
        assertThat(payload.path("machineType").asString()).isEqualTo("Cardado");
        assertThat(event.payload().toString()).as("el código interno de la máquina no viaja").doesNotContain("MQ-6");
        assertThat(payload.path("wasteReason").asString()).isEqualTo("Orillo");
        assertThat(payload.path("type").asString()).isEqualTo("DYEING");
        assertThat(payload.path("machineId").asString()).isEqualTo(machine.toString());
        assertThat(payload.path("inputs").get(0).path("batchId").asString()).isEqualTo(idOf(input).toString());
        assertThat(payload.path("inputs").get(0).path("quantityKg").decimalValue()).isEqualByComparingTo("100");
        assertThat(payload.path("wasteKg").decimalValue()).isEqualByComparingTo("5");
        assertThat(payload.path("shrinkageKg").decimalValue()).isEqualByComparingTo("5");
        assertThat(payload.path("outputs")).hasSize(2);
        JsonNode finalOutput = payload.path("outputs").get(1);
        assertThat(finalOutput.propertyNames()).containsExactlyInAnyOrder("batchId", "traceabilityId", "qrCode", "materialType",
                "quantityKg", "composition", "finalProduct", "name");
        // sin nombre en la petición, la salida se llama como su Traceability ID
        assertThat(finalOutput.path("name").asString()).isEqualTo(completed.path("outputs").get(1).path("name").asString())
                .isEqualTo(finalOutput.path("traceabilityId").asString());
        assertThat(finalOutput.path("batchId").asString()).isEqualTo(completed.path("outputs").get(1).path("id").asString());
        assertThat(finalOutput.path("finalProduct").asBoolean()).isTrue();
        assertThat(payload.path("outputs").get(0).path("finalProduct").asBoolean()).isFalse();
        assertThat(event.payload().toString()).doesNotContain("Tiendas", "salePrice", "buyerOrDistributor", "currency");
        assertThat(outbox().stream().map(Event::payload).map(JsonNode::toString)).noneMatch(text -> text.contains("createdBy"));
    }

    @Test
    void transferEventsCarryTheCatalogFields() throws Exception {
        JsonNode a = registerBatch(10);
        clearOutbox();
        UUID transfer = idOf(read(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B,
                "batchIds", List.of(idOf(a)))).andExpect(status().isCreated())));
        postAs(ADMIN_OF_B, "/api/transfers/" + transfer + "/accept").andExpect(status().isOk());

        List<Event> events = outbox().stream().filter(e -> e.type().startsWith("Transfer")).toList();

        assertThat(events).extracting(Event::type).containsExactly("TransferInitiated", "TransferAccepted");
        JsonNode initiated = events.get(0).payload().path("payload");
        assertThat(initiated.propertyNames()).containsExactlyInAnyOrder("transferId", "batchIds", "fromCompanyId", "toCompanyId");
        assertThat(initiated.path("fromCompanyId").asString()).isEqualTo(COMPANY_A.toString());
        assertThat(initiated.path("toCompanyId").asString()).isEqualTo(COMPANY_B.toString());
        assertThat(initiated.path("batchIds").get(0).asString()).isEqualTo(idOf(a).toString());
        JsonNode accepted = events.get(1).payload().path("payload");
        assertThat(accepted.propertyNames()).containsExactlyInAnyOrder("transferId", "batchIds", "fromCompanyId", "toCompanyId", "acceptedAt");
        assertThat(accepted.path("acceptedAt").asString()).isNotBlank();
    }

    @Test
    void theWasteReasonTravelsWithTheTransformationAndIsNullWhenThereWasNoWaste() throws Exception {
        UUID machine = idOf(registerMachine("MQ-WR"));
        UUID withWaste = idOf(startTransformation("SPINNING", machine, idOf(registerBatch(10))));
        postAs(OPERATOR_OF_A, "/api/transformations/" + withWaste + "/complete",
                obj("outputs", List.of(obj("materialType", "Hilo", "quantityKg", 8)), "wasteKg", 1, "wasteReason", "Orillo de la tela"))
                .andExpect(status().isOk());
        UUID withoutWaste = idOf(startTransformation("SPINNING", machine, idOf(registerBatch(10))));
        postAs(OPERATOR_OF_A, "/api/transformations/" + withoutWaste + "/complete",
                obj("outputs", List.of(obj("materialType", "Hilo", "quantityKg", 9)))).andExpect(status().isOk());

        JsonNode first = outbox().stream().filter(e -> e.type().equals("TransformationCompleted") && e.aggregateId().equals(withWaste.toString()))
                .findFirst().orElseThrow().payload().path("payload");
        JsonNode second = outbox().stream().filter(e -> e.type().equals("TransformationCompleted") && e.aggregateId().equals(withoutWaste.toString()))
                .findFirst().orElseThrow().payload().path("payload");

        assertThat(first.path("wasteReason").asString()).isEqualTo("Orillo de la tela");
        assertThat(second.path("wasteKg").decimalValue()).isEqualByComparingTo("0");
        assertThat(second.path("wasteReason").isNull() || second.path("wasteReason").isMissingNode()).isTrue();
    }

    // ---- utilidades ----

    private record Event(UUID id, String type, String aggregateId, String topic, JsonNode payload) {
    }

    private List<Event> outbox() {
        return jdbc.sql("SELECT id, event_type, aggregate_id, topic, payload::text AS payload FROM outbox ORDER BY created_at").query((rs, n) -> {
            try {
                return new Event(UUID.fromString(rs.getString("id")), rs.getString("event_type"), rs.getString("aggregate_id"),
                        rs.getString("topic"), json.readTree(rs.getString("payload")));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).list();
    }

    private List<List<String>> payload(List<Event> events) {
        return events.stream().map(e -> e.payload().path("payload").propertyNames().stream().toList()).toList();
    }

    private void clearOutbox() {
        jdbc.sql("DELETE FROM outbox").update();
    }

    private static Phase phase(JsonNode batch, UUID company, String previous, String next, boolean hold, String reason, UUID requestId) {
        return new Phase(UUID.fromString(batch.path("id").asString()), company, previous, next, hold, reason, requestId.toString());
    }

    /** Un producto final ya creado por la API (transformación de un lote y finalización con una salida final). */
    private JsonNode completedFinalProduct() throws Exception {
        UUID transformation = idOf(startTransformation("DYEING", idOf(registerMachine("MQ-F" + UUID.randomUUID().toString().substring(0, 6))),
                idOf(registerBatch(100))));
        return read(postAs(OPERATOR_OF_A, "/api/transformations/" + transformation + "/complete", obj("outputs", List.of(
                obj("materialType", "Tela", "quantityKg", 90, "finalProduct", true)))).andExpect(status().isOk())).path("outputs").get(0);
    }

    /** Todas las rutas de escritura (todo menos GET) que mapean los controladores de este servicio. */
    private Set<String> writeRoutes() {
        Set<String> routes = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            if (!method.getBeanType().getName().startsWith("com.nodotextil.trazatex.production")) {
                return;
            }
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (methods.isEmpty()) {
                    routes.add("ANY " + pattern);
                }
                for (RequestMethod httpMethod : methods) {
                    if (httpMethod != RequestMethod.GET && httpMethod != RequestMethod.HEAD && httpMethod != RequestMethod.OPTIONS) {
                        routes.add(httpMethod + " " + pattern);
                    }
                }
            }
        });
        return routes;
    }
}
