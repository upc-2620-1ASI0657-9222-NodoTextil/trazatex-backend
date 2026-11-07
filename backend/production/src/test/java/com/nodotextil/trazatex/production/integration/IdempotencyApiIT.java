package com.nodotextil.trazatex.production.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADR-010: toda escritura expuesta al frontend acepta {@code Idempotency-Key} y no duplica al reintentar. */
class IdempotencyApiIT extends ApiTestBase {

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    /**
     * La repetición devuelve el cuerpo guardado en una columna JSONB, que PostgreSQL normaliza (orden de claves y
     * espacios): el texto puede diferir del original aunque el JSON sea el mismo. Se compara el JSON interpretado.
     */
    private JsonNode parsed(String body) throws Exception {
        return json.readTree(body);
    }

    @Test
    void registeringTwiceWithTheSameKeyCreatesOneBatchAndRepeatsTheResponse() throws Exception {
        String key = key();
        MvcResult first = postWithKey(OPERATOR_OF_A, "/api/batches", batchBody(10), key).andExpect(status().isCreated()).andReturn();
        MvcResult second = postWithKey(OPERATOR_OF_A, "/api/batches", batchBody(10), key).andExpect(status().isCreated()).andReturn();

        assertThat(parsed(bodyOf(second))).isEqualTo(parsed(bodyOf(first)));
        assertThat(count("SELECT count(*) FROM batches")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'BATCH_REGISTERED'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM idempotency_keys")).isEqualTo(1);
    }

    @Test
    void withoutAKeyTwoRegistrationsAreTwoBatches() throws Exception {
        postAs(OPERATOR_OF_A, "/api/batches", batchBody(10)).andExpect(status().isCreated());
        postAs(OPERATOR_OF_A, "/api/batches", batchBody(10)).andExpect(status().isCreated());

        assertThat(count("SELECT count(*) FROM batches")).isEqualTo(2);
    }

    @Test
    void theSameKeyWithAnotherBodyOrAnotherUserIsRejected() throws Exception {
        String key = key();
        postWithKey(OPERATOR_OF_A, "/api/batches", batchBody(10), key).andExpect(status().isCreated());

        assertThat(code(postWithKey(OPERATOR_OF_A, "/api/batches", batchBody(11), key).andExpect(status().isUnprocessableEntity())))
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(code(postWithKey(OPERATOR_OF_B, "/api/batches", batchBody(10), key).andExpect(status().isUnprocessableEntity())))
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(count("SELECT count(*) FROM batches")).isEqualTo(1);
    }

    @Test
    void aFailedRequestDoesNotKeepTheKeySoTheCorrectedRetryWorks() throws Exception {
        String key = key();
        var invalid = batchBody(10);
        invalid.put("composition", List.of(component("Algodón", 50)));
        postWithKey(OPERATOR_OF_A, "/api/batches", invalid, key).andExpect(status().isBadRequest());
        assertThat(count("SELECT count(*) FROM idempotency_keys")).as("un 400 no deja la clave").isZero();

        postWithKey(OPERATOR_OF_A, "/api/batches", batchBody(10), key).andExpect(status().isCreated());

        assertThat(count("SELECT count(*) FROM batches")).isEqualTo(1);
    }

    @Test
    void aConflictIsNotCachedEither() throws Exception {
        JsonNode batch = registerBatch(100);
        String key = key();
        var children = obj("children", List.of(obj("quantityKg", 40), obj("quantityKg", 70)));
        postWithKey(OPERATOR_OF_A, "/api/batches/" + batch.path("id").asString() + "/split", children, key).andExpect(status().isBadRequest());

        assertThat(count("SELECT count(*) FROM idempotency_keys")).isZero();
    }

    @Test
    void anInvalidKeyIsA400() throws Exception {
        assertThat(code(postWithKey(OPERATOR_OF_A, "/api/batches", batchBody(10), "no-es-uuid").andExpect(status().isBadRequest())))
                .isEqualTo("INVALID_IDEMPOTENCY_KEY");
        assertThat(count("SELECT count(*) FROM batches")).isZero();
    }

    @Test
    void anExpiredKeyIsExecutedAgain() throws Exception {
        String key = key();
        postWithKey(OPERATOR_OF_A, "/api/batches", batchBody(10), key).andExpect(status().isCreated());
        clock.advance(Duration.ofHours(25));

        postWithKey(OPERATOR_OF_A, "/api/batches", batchBody(10), key).andExpect(status().isCreated());

        assertThat(count("SELECT count(*) FROM batches")).as("la clave caducó a las 24 h").isEqualTo(2);
    }

    @Test
    void splittingWithTheSameKeyDoesNotSplitAgainNorFailWithAConflict() throws Exception {
        JsonNode parent = registerBatch(100);
        String url = "/api/batches/" + parent.path("id").asString() + "/split";
        var children = obj("children", List.of(obj("quantityKg", 40), obj("quantityKg", 60)));
        String key = key();

        MvcResult first = postWithKey(OPERATOR_OF_A, url, children, key).andExpect(status().isCreated()).andReturn();
        MvcResult retry = postWithKey(OPERATOR_OF_A, url, children, key).andExpect(status().isCreated()).andReturn();

        assertThat(parsed(bodyOf(retry))).isEqualTo(parsed(bodyOf(first)));
        assertThat(count("SELECT count(*) FROM batches")).as("padre + 2 hijos, no 4").isEqualTo(3);
    }

    @Test
    void theWholeProductionFlowIsSafeToRetry() throws Exception {
        JsonNode batch = registerBatch(100);
        UUID machine = idOf(registerMachine("MQ-01"));

        // iniciar transformación
        String startKey = key();
        var start = obj("type", "SPINNING", "machineId", machine, "batchIds", List.of(idOf(batch)));
        String started = bodyOf(postWithKey(OPERATOR_OF_A, "/api/transformations", start, startKey).andExpect(status().isCreated()).andReturn());
        assertThat(parsed(bodyOf(postWithKey(OPERATOR_OF_A, "/api/transformations", start, startKey).andExpect(status().isCreated()).andReturn()))).isEqualTo(parsed(started));
        assertThat(count("SELECT count(*) FROM transformations")).isEqualTo(1);

        // finalizar
        String id = json.readTree(started).path("id").asString();
        String completeKey = key();
        var complete = obj("outputs", List.of(output("Hilo", 90)));
        String completed = bodyOf(postWithKey(OPERATOR_OF_A, "/api/transformations/" + id + "/complete", complete, completeKey)
                .andExpect(status().isOk()).andReturn());
        assertThat(parsed(bodyOf(postWithKey(OPERATOR_OF_A, "/api/transformations/" + id + "/complete", complete, completeKey)
                .andExpect(status().isOk()).andReturn()))).isEqualTo(parsed(completed));
        assertThat(count("SELECT count(*) FROM batches WHERE origin_type = 'TRANSFORMATION'")).isEqualTo(1);

        // iniciar transferencia y aceptarla
        String output = json.readTree(completed).path("outputs").get(0).path("id").asString();
        String transferKey = key();
        var transfer = obj("targetCompanyId", COMPANY_B, "batchIds", List.of(output));
        String initiated = bodyOf(postWithKey(OPERATOR_OF_A, "/api/transfers", transfer, transferKey).andExpect(status().isCreated()).andReturn());
        assertThat(parsed(bodyOf(postWithKey(OPERATOR_OF_A, "/api/transfers", transfer, transferKey).andExpect(status().isCreated()).andReturn())))
                .isEqualTo(parsed(initiated));
        assertThat(count("SELECT count(*) FROM transfers")).isEqualTo(1);

        String transferId = json.readTree(initiated).path("id").asString();
        String acceptKey = key();
        String accepted = bodyOf(postWithKey(ADMIN_OF_B, "/api/transfers/" + transferId + "/accept", obj(), acceptKey).andExpect(status().isOk()).andReturn());
        assertThat(parsed(bodyOf(postWithKey(ADMIN_OF_B, "/api/transfers/" + transferId + "/accept", obj(), acceptKey).andExpect(status().isOk()).andReturn())))
                .as("aceptar de nuevo con la misma clave no da 409").isEqualTo(parsed(accepted));
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'TRANSFER_ACCEPTED'")).isEqualTo(1);
    }

    @Test
    void registeringAMachineWithTheSameKeyDoesNotFailAsADuplicate() throws Exception {
        String key = key();
        var machine = obj("internalCode", "MQ-01", "name", "Cardadora", "type", "Cardado");

        MvcResult first = postWithKey(ADMIN_OF_A, "/api/machines", machine, key).andExpect(status().isCreated()).andReturn();
        MvcResult retry = postWithKey(ADMIN_OF_A, "/api/machines", machine, key).andExpect(status().isCreated()).andReturn();

        assertThat(parsed(bodyOf(retry))).isEqualTo(parsed(bodyOf(first)));
        assertThat(count("SELECT count(*) FROM machines")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM machine_status_history")).isEqualTo(1);
    }

    @Test
    void twoSimultaneousRequestsWithTheSameKeyCreateOneBatch() throws Exception {
        for (int round = 0; round < 6; round++) {
            String key = key();
            ExecutorService pool = Executors.newFixedThreadPool(4);
            CyclicBarrier barrier = new CyclicBarrier(4);
            try {
                List<Future<MvcResult>> futures = new java.util.ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    Callable<MvcResult> task = () -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        return postWithKey(OPERATOR_OF_A, "/api/batches", batchBody(10), key).andReturn();
                    };
                    futures.add(pool.submit(task));
                }
                List<JsonNode> bodies = new java.util.ArrayList<>();
                for (Future<MvcResult> future : futures) {
                    MvcResult result = future.get(30, TimeUnit.SECONDS);
                    assertThat(result.getResponse().getStatus()).isEqualTo(201);
                    bodies.add(parsed(bodyOf(result)));
                }
                assertThat(bodies).as("las cuatro respuestas son la misma").containsOnly(bodies.get(0));
            } finally {
                pool.shutdownNow();
            }
        }
        assertThat(count("SELECT count(*) FROM batches")).as("6 rondas × 4 peticiones, una clave por ronda").isEqualTo(6);
    }
}
