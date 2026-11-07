package com.nodotextil.trazatex.production.integration;

import com.nodotextil.trazatex.production.application.port.out.BatchRepositoryPort;
import com.nodotextil.trazatex.production.domain.model.Batch;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comando interno de Quality (ADR-009) por HTTP: contrato, códigos (409 solo para el rechazo de negocio), repetición idempotente,
 * empresa inactiva (solo {@code START_EVALUATION} se bloquea) y la reacción a una carrera no resuelta (503, nunca 409).
 */
class InternalPhaseTransitionApiIT extends ApiTestBase {

    @MockitoSpyBean
    BatchRepositoryPort batchRepository;

    private JsonNode ok(ResultActions result) throws Exception {
        return read(result.andExpect(status().isOk()));
    }

    private String phaseOf(UUID batch) {
        return jdbc.sql("SELECT current_stage FROM batches WHERE id = :id").param("id", batch).query(String.class).single();
    }

    private boolean holdOf(UUID batch) {
        return jdbc.sql("SELECT quality_hold FROM batches WHERE id = :id").param("id", batch).query(Boolean.class).single();
    }

    private long eventsOf(UUID batch) {
        return count("SELECT count(*) FROM outbox WHERE event_type = 'BatchPhaseChanged' AND aggregate_id = ?", batch.toString());
    }

    // ---- las cinco operaciones ----

    @Test
    void theFiveOperationsMoveThePhaseAndTheHoldAndRespondWithTheResult() throws Exception {
        UUID batch = idOf(registerBatch(10));

        JsonNode started = ok(internalCommand(batch, phaseCommand("START_EVALUATION", "ctl-1")));
        assertThat(started.propertyNames()).containsExactlyInAnyOrder("batchId", "phase", "qualityHold");
        assertThat(started.path("batchId").asString()).isEqualTo(batch.toString());
        assertThat(started.path("phase").asString()).isEqualTo("IN_QUALITY_CONTROL");
        assertThat(started.path("qualityHold").asBoolean()).isFalse();

        JsonNode failed = ok(internalCommand(batch, phaseCommand("RELEASE", "ctl-1", true, "Control con falla")));
        assertThat(failed.path("phase").asString()).isEqualTo("AVAILABLE");
        assertThat(failed.path("qualityHold").asBoolean()).as("RELEASE fija la retención en el mismo cambio").isTrue();
        assertThat(holdOf(batch)).isTrue();

        JsonNode freed = ok(internalCommand(batch, phaseCommand("RELEASE_HOLD", "fail-1")));
        assertThat(freed.path("qualityHold").asBoolean()).isFalse();

        JsonNode held = ok(internalCommand(batch, obj("operation", "HOLD", "requestId", "derived-1")));
        assertThat(held.path("phase").asString()).isEqualTo("AVAILABLE");
        assertThat(held.path("qualityHold").asBoolean()).isTrue();

        JsonNode discarded = ok(internalCommand(batch, phaseCommand("DISCARD", "fail-1")));
        assertThat(discarded.path("phase").asString()).isEqualTo("DISCARDED");
        assertThat(phaseOf(batch)).isEqualTo("DISCARDED");
        assertThat(eventsOf(batch)).as("un BatchPhaseChanged por cada cambio").isEqualTo(5);
    }

    @Test
    void aConformingReleaseLeavesTheBatchEnabledAgain() throws Exception {
        UUID batch = idOf(registerBatch(10));
        internalCommand(batch, phaseCommand("START_EVALUATION", "ctl-1")).andExpect(status().isOk());

        ok(internalCommand(batch, phaseCommand("RELEASE", "ctl-1", false, null)));

        postAs(OPERATOR_OF_A, "/api/batches/" + batch + "/split", obj("children", List.of(obj("quantityKg", 4), obj("quantityKg", 6))))
                .andExpect(status().isCreated());
    }

    // ---- sin identidad, sin roles, sin Idempotency-Key ----

    @Test
    void theRouteNeedsNoIdentityAndIgnoresIdentityAndIdempotencyHeaders() throws Exception {
        UUID batch = idOf(registerBatch(10));

        // sin ninguna cabecera: 200
        ok(internalCommand(batch, phaseCommand("HOLD", "a")));
        // con cabeceras de identidad absurdas y una Idempotency-Key inválida: se ignoran (no hay 401, 403, 400 ni 422)
        ok(mockMvc.perform(post("/internal/batches/" + batch + "/phase-transitions").contentType("application/json")
                .header("X-User-Id", "no-es-un-uuid").header("X-User-Role", "ROL_INEXISTENTE").header("Idempotency-Key", "tampoco")
                .header(HttpHeaders.AUTHORIZATION, "Bearer cualquier-cosa")
                .content(json.writeValueAsString(phaseCommand("RELEASE_HOLD", "b")))));

        assertThat(count("SELECT count(*) FROM idempotency_keys")).as("no pasa por el IdempotencyFilter").isZero();
    }

    // ---- 404 y 400 ----

    @Test
    void aBatchThatDoesNotExistIs404() throws Exception {
        JsonNode error = read(internalCommand(UUID.randomUUID(), phaseCommand("HOLD", "a")).andExpect(status().isNotFound()));

        assertThat(error.path("code").asString()).isEqualTo("BATCH_NOT_FOUND");
    }

    @Test
    void invalidRequestsAre400WithTheMissingFieldsListedAndChangeNothing() throws Exception {
        UUID batch = idOf(registerBatch(10));
        internalCommand(batch, phaseCommand("START_EVALUATION", "ctl-1")).andExpect(status().isOk());
        jdbc.sql("DELETE FROM outbox").update();

        JsonNode nothing = read(internalCommand(batch, "{}").andExpect(status().isBadRequest()));
        assertThat(nothing.path("code").asString()).isEqualTo("MISSING_FIELDS");
        assertThat(nothing.path("details")).extracting(JsonNode::asString).containsExactly("operation", "requestId");
        assertThat(nothing.propertyNames()).containsExactlyInAnyOrder("code", "message", "details", "timestamp");

        JsonNode releaseWithoutHold = read(internalCommand(batch, obj("operation", "RELEASE", "requestId", "ctl-1", "requestedBy", OPERATOR_A))
                .andExpect(status().isBadRequest()));
        assertThat(releaseWithoutHold.path("code").asString()).isEqualTo("MISSING_FIELDS");
        assertThat(releaseWithoutHold.path("details")).extracting(JsonNode::asString).containsExactly("qualityHold");

        for (String operation : List.of("START_EVALUATION", "RELEASE", "DISCARD", "RELEASE_HOLD")) {
            JsonNode error = read(internalCommand(batch, obj("operation", operation, "requestId", "x-" + operation, "qualityHold", false))
                    .andExpect(status().isBadRequest()));
            assertThat(error.path("details")).as(operation).extracting(JsonNode::asString).containsExactly("requestedBy");
        }
        assertThat(code(internalCommand(batch, obj("operation", "HOLD", "requestId", "x".repeat(65))).andExpect(status().isBadRequest())))
                .isEqualTo("INVALID_REQUEST_ID");
        assertThat(code(internalCommand(batch, obj("operation", "EXPLOTAR", "requestId", "a", "requestedBy", OPERATOR_A))
                .andExpect(status().isBadRequest()))).isEqualTo("MALFORMED_REQUEST");
        assertThat(code(internalCommand(batch, obj("operation", "DISCARD", "requestId", "a", "requestedBy", "no-uuid"))
                .andExpect(status().isBadRequest()))).isEqualTo("MALFORMED_REQUEST");
        assertThat(code(internalCommand(batch, "{no es json").andExpect(status().isBadRequest()))).isEqualTo("MALFORMED_REQUEST");

        assertThat(phaseOf(batch)).isEqualTo("IN_QUALITY_CONTROL");
        assertThat(count("SELECT count(*) FROM outbox")).isZero();
        assertThat(count("SELECT count(*) FROM phase_command_log")).isEqualTo(1);
    }

    @Test
    void holdDoesNotNeedRequestedByAndTheAuditRecordsASystemAction() throws Exception {
        UUID batch = idOf(registerBatch(10));

        ok(internalCommand(batch, obj("operation", "HOLD", "requestId", "derived-1", "reason", "Posible falla derivada")));

        assertThat(jdbc.sql("SELECT user_id IS NULL FROM audit_log WHERE action = 'BATCH_PHASE_COMMAND'").query(Boolean.class).single()).isTrue();
        JsonNode event = json.readTree(jdbc.sql("SELECT payload::text FROM outbox WHERE event_type = 'BatchPhaseChanged'").query(String.class).single());
        assertThat(event.path("payload").path("reason").asString()).isEqualTo("Posible falla derivada");
        assertThat(event.path("payload").path("requestId").asString()).isEqualTo("derived-1");
    }

    // ---- 409: solo el rechazo de negocio, con su código ----

    @Test
    void aBusinessRejectionIs409WithItsReasonCodeAndLeavesNoTrace() throws Exception {
        UUID inControl = idOf(registerBatch(10));
        internalCommand(inControl, phaseCommand("START_EVALUATION", "ctl-1")).andExpect(status().isOk());
        UUID finalProduct = idOf(completedFinalProduct());
        UUID available = idOf(registerBatch(10));
        jdbc.sql("DELETE FROM outbox").update();
        long logRows = count("SELECT count(*) FROM phase_command_log");

        for (String operation : List.of("START_EVALUATION", "DISCARD", "HOLD", "RELEASE_HOLD")) {
            JsonNode error = read(internalCommand(inControl, phaseCommand(operation, "otro-" + operation)).andExpect(status().isConflict()));
            assertThat(error.path("code").asString()).as(operation + " sobre un lote en control").isEqualTo("INVALID_PHASE_TRANSITION");
            assertThat(error.propertyNames()).containsExactlyInAnyOrder("code", "message", "details", "timestamp");
        }
        assertThat(code(internalCommand(available, phaseCommand("RELEASE", "ctl-9")).andExpect(status().isBadRequest()))).isEqualTo("MISSING_FIELDS");
        assertThat(code(internalCommand(available, phaseCommand("RELEASE", "ctl-9", false, null)).andExpect(status().isConflict())))
                .isEqualTo("INVALID_PHASE_TRANSITION");
        assertThat(code(internalCommand(finalProduct, phaseCommand("START_EVALUATION", "ctl-2")).andExpect(status().isConflict())))
                .as("un producto final no se evalúa (RF-036)").isEqualTo("BATCH_IS_FINAL_PRODUCT");

        assertThat(phaseOf(inControl)).isEqualTo("IN_QUALITY_CONTROL");
        assertThat(count("SELECT count(*) FROM outbox")).isZero();
        assertThat(count("SELECT count(*) FROM phase_command_log")).as("un rechazo no se guarda").isEqualTo(logRows);
        // y como no se guardó, repetirlo se vuelve a evaluar: tras liberar el lote, el mismo comando sí prospera
        internalCommand(inControl, phaseCommand("RELEASE", "ctl-1", false, null)).andExpect(status().isOk());
        ok(internalCommand(inControl, phaseCommand("HOLD", "otro-HOLD")));
    }

    private JsonNode completedFinalProduct() throws Exception {
        UUID transformation = idOf(startTransformation("DYEING", idOf(registerMachine("MQ-" + UUID.randomUUID().toString().substring(0, 6))),
                idOf(registerBatch(100))));
        return read(postAs(OPERATOR_OF_A, "/api/transformations/" + transformation + "/complete", obj("outputs", List.of(
                obj("materialType", "Tela", "quantityKg", 90, "finalProduct", true)))).andExpect(status().isOk())).path("outputs").get(0);
    }

    // ---- repetición idempotente ----

    @Test
    void repeatingACommandReturnsTheSameResponseWithoutChangingAnythingOrPublishing() throws Exception {
        UUID batch = idOf(registerBatch(10));
        String first = internalCommand(batch, phaseCommand("START_EVALUATION", "ctl-1")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        long events = eventsOf(batch);

        String again = internalCommand(batch, phaseCommand("START_EVALUATION", "ctl-1")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();

        assertThat(json.readTree(again)).isEqualTo(json.readTree(first));
        assertThat(eventsOf(batch)).isEqualTo(events);
        assertThat(count("SELECT count(*) FROM phase_command_log WHERE batch_id = ?", batch)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'BATCH_PHASE_COMMAND'")).isEqualTo(1);
    }

    @Test
    void theStoredResultWinsEvenIfTheBatchMovedOnAndTheOperationWouldNowBeRejected() throws Exception {
        UUID batch = idOf(registerBatch(10));
        JsonNode first = ok(internalCommand(batch, phaseCommand("START_EVALUATION", "ctl-1")));
        internalCommand(batch, phaseCommand("RELEASE", "ctl-1", false, null)).andExpect(status().isOk());
        internalCommand(batch, phaseCommand("DISCARD", "ctl-1")).andExpect(status().isOk());
        // y la empresa se inactiva: START_EVALUATION ya no se aceptaría
        jdbc.sql("UPDATE company_projection SET status = 'INACTIVE' WHERE company_id = :c").param("c", COMPANY_A).update();
        long events = eventsOf(batch);

        JsonNode again = ok(internalCommand(batch, phaseCommand("START_EVALUATION", "ctl-1")));

        assertThat(phaseOf(batch)).isEqualTo("DISCARDED");
        assertThat(again).as("la respuesta de la primera vez, no el estado de hoy").isEqualTo(first);
        assertThat(again.path("phase").asString()).isEqualTo("IN_QUALITY_CONTROL");
        assertThat(eventsOf(batch)).isEqualTo(events);
    }

    @Test
    void theSameRequestIdWithAnotherOperationIsAnotherCommand() throws Exception {
        UUID batch = idOf(registerBatch(10));

        ok(internalCommand(batch, phaseCommand("START_EVALUATION", "ctl-1")));
        JsonNode release = ok(internalCommand(batch, phaseCommand("RELEASE", "ctl-1", true, null)));

        assertThat(release.path("phase").asString()).isEqualTo("AVAILABLE");
        assertThat(count("SELECT count(*) FROM phase_command_log WHERE request_id = 'ctl-1'")).isEqualTo(2);
    }

    @Test
    void holdAndReleaseHoldWithoutChangeAre200WithoutEventAndAreRememberedToo() throws Exception {
        UUID batch = idOf(registerBatch(10));
        ok(internalCommand(batch, phaseCommand("HOLD", "f-1")));
        long events = eventsOf(batch);

        JsonNode second = ok(internalCommand(batch, phaseCommand("HOLD", "f-2")));
        JsonNode unheld = ok(internalCommand(batch, phaseCommand("RELEASE_HOLD", "f-1")));
        ok(internalCommand(batch, phaseCommand("RELEASE_HOLD", "f-3")));          // ya estaba libre: sin cambio
        JsonNode replayOfFirstHold = ok(internalCommand(batch, phaseCommand("HOLD", "f-1")));

        assertThat(second.path("qualityHold").asBoolean()).isTrue();
        assertThat(unheld.path("qualityHold").asBoolean()).isFalse();
        assertThat(replayOfFirstHold.path("qualityHold").asBoolean()).as("lo que devolvió la primera vez").isTrue();
        assertThat(eventsOf(batch)).as("solo el HOLD inicial y el RELEASE_HOLD que sí cambió").isEqualTo(events + 1);
        assertThat(count("SELECT count(*) FROM phase_command_log WHERE batch_id = ?", batch)).isEqualTo(4);
    }

    // ---- empresa inactiva (RF-003): solo START_EVALUATION se bloquea ----

    @Test
    void anInactiveCompanyIsBlockedOnlyForStartEvaluation() throws Exception {
        UUID inControl = idOf(registerBatch(10));
        internalCommand(inControl, phaseCommand("START_EVALUATION", "ctl-1")).andExpect(status().isOk());
        UUID available = idOf(registerBatch(10));
        UUID toDiscard = idOf(registerBatch(10));
        jdbc.sql("UPDATE company_projection SET status = 'INACTIVE' WHERE company_id = :c").param("c", COMPANY_A).update();

        assertThat(code(internalCommand(available, phaseCommand("START_EVALUATION", "ctl-2")).andExpect(status().isConflict())))
                .isEqualTo("COMPANY_INACTIVE");
        assertThat(phaseOf(available)).isEqualTo("AVAILABLE");

        ok(internalCommand(inControl, phaseCommand("RELEASE", "ctl-1", true, null)));        // el lote no queda atrapado
        ok(internalCommand(inControl, phaseCommand("RELEASE_HOLD", "fail-1")));
        ok(internalCommand(available, phaseCommand("HOLD", "derived-1")));
        ok(internalCommand(toDiscard, phaseCommand("DISCARD", "ctl-3")));
        assertThat(phaseOf(inControl)).isEqualTo("AVAILABLE");
        assertThat(holdOf(available)).isTrue();
        assertThat(phaseOf(toDiscard)).isEqualTo("DISCARDED");
    }

    @Test
    void anUnknownCompanyIsBlockedOnlyForStartEvaluation() throws Exception {
        UUID batch = idOf(registerBatch(10));
        jdbc.sql("DELETE FROM company_projection WHERE company_id = :c").param("c", COMPANY_A).update();

        assertThat(code(internalCommand(batch, phaseCommand("START_EVALUATION", "ctl-1")).andExpect(status().isConflict())))
                .isEqualTo("COMPANY_UNKNOWN");
        ok(internalCommand(batch, phaseCommand("HOLD", "derived-1")));
        ok(internalCommand(batch, phaseCommand("DISCARD", "ctl-2")));
    }

    // ---- carrera no resuelta: 503, nunca 409 ----

    @Test
    void aVersionConflictWithAnotherOperationIsAbsorbedByTheInternalRetry() throws Exception {
        UUID batch = idOf(registerBatch(10));
        AtomicInteger saves = new AtomicInteger();
        doAnswer(invocation -> {
            if (saves.getAndIncrement() == 0) {
                // otra operación modifica el lote y confirma entre la lectura y el guardado de este comando
                CompletableFuture.runAsync(() -> jdbc.sql("UPDATE batches SET version = version + 1 WHERE id = :id").param("id", batch).update()).join();
            }
            return invocation.callRealMethod();
        }).when(batchRepository).save(any(Batch.class));
        jdbc.sql("DELETE FROM outbox").update();

        JsonNode result = ok(internalCommand(batch, phaseCommand("START_EVALUATION", "ctl-1")));

        assertThat(result.path("phase").asString()).isEqualTo("IN_QUALITY_CONTROL");
        assertThat(saves.get()).as("el primer intento chocó con la versión; el segundo guardó").isEqualTo(2);
        assertThat(eventsOf(batch)).as("un solo BatchPhaseChanged: el del intento que se confirmó").isEqualTo(1);
        assertThat(count("SELECT count(*) FROM phase_command_log WHERE batch_id = ?", batch)).isEqualTo(1);
    }

    @Test
    void aConflictThatNeverGoesAwayIs503WithRetryAfterAndLeavesNothing() throws Exception {
        UUID batch = idOf(registerBatch(10));
        doAnswer(invocation -> {
            CompletableFuture.runAsync(() -> jdbc.sql("UPDATE batches SET version = version + 1 WHERE id = :id").param("id", batch).update()).join();
            return invocation.callRealMethod();
        }).when(batchRepository).save(any(Batch.class));
        jdbc.sql("DELETE FROM outbox").update();

        ResultActions result = internalCommand(batch, phaseCommand("START_EVALUATION", "ctl-1"));

        result.andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "1"));
        JsonNode error = read(result);
        assertThat(error.path("code").asString()).isEqualTo("TEMPORARILY_UNAVAILABLE");
        assertThat(phaseOf(batch)).isEqualTo("AVAILABLE");
        assertThat(count("SELECT count(*) FROM outbox")).isZero();
        assertThat(count("SELECT count(*) FROM phase_command_log")).isZero();
    }
}
