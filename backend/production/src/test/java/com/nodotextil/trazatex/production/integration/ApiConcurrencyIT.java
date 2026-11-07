package com.nodotextil.trazatex.production.integration;

import org.junit.jupiter.api.RepeatedTest;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las mismas carreras de {@code ConcurrencyIT}, pero por HTTP contra los casos de uso reales (no simulados): comprueba que
 * los servicios bloquean los lotes antes de decidir. De dos peticiones simultáneas sobre el mismo lote prospera una y la
 * otra recibe 409 (nunca un 500 ni una violación de restricción). Cada escenario se repite 6 veces.
 */
class ApiConcurrencyIT extends ApiTestBase {

    /** Respuestas de la última carrera (estado y código), para comprobar quién perdió y por qué. */
    private final List<String> lastCodes = new java.util.concurrent.CopyOnWriteArrayList<>();

    private List<Integer> race(List<Callable<MvcResult>> tasks) throws Exception {
        lastCodes.clear();
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CyclicBarrier start = new CyclicBarrier(tasks.size());
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<MvcResult> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    MvcResult result = task.call();
                    String body = result.getResponse().getContentAsString();
                    lastCodes.add(result.getResponse().getStatus() + ":" + (body.contains("\"code\"") ? json.readTree(body).path("code").asString() : ""));
                    return result.getResponse().getStatus();
                }));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private void assertOneWinner(List<Integer> statuses, int winnerStatus) {
        assertThat(statuses.stream().filter(s -> s == winnerStatus).count()).as("estados: %s", statuses).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s != winnerStatus)).as("los demás son 409: %s", statuses).allMatch(s -> s == 409);
        // El perdedor espera al bloqueo de fila, ve el estado nuevo y recibe el conflicto del DOMINIO. Si recibiera
        // CONCURRENT_MODIFICATION (bloqueo optimista) o CONFLICT (restricción de la base) significaría que no se bloqueó antes de decidir.
        assertThat(lastCodes).as("códigos: %s", lastCodes).noneMatch(c -> c.endsWith(":CONCURRENT_MODIFICATION") || c.equals("409:CONFLICT"));
    }

    @RepeatedTest(6)
    void sixSimultaneousTransformationsOfTheSameBatchStartOnlyOne() throws Exception {
        UUID machine = idOf(registerMachine("MQ-" + UUID.randomUUID().toString().substring(0, 8)));
        UUID batch = idOf(registerBatch(100));
        var body = obj("type", "SPINNING", "machineId", machine, "batchIds", List.of(batch));
        List<Callable<MvcResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> postAs(OPERATOR_OF_A, "/api/transformations", body).andReturn());
        }

        assertOneWinner(race(tasks), 201);

        assertThat(count("SELECT count(*) FROM transformations")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'TRANSFORMATION_STARTED'")).isEqualTo(1);
    }

    @RepeatedTest(6)
    void aSplitAndATransferOfTheSameBatchOnlyOneWins() throws Exception {
        UUID batch = idOf(registerBatch(100));
        var split = obj("children", List.of(obj("quantityKg", 40), obj("quantityKg", 60)));
        var transfer = obj("targetCompanyId", COMPANY_B, "batchIds", List.of(batch));

        List<Integer> statuses = race(List.of(
                () -> postAs(OPERATOR_OF_A, "/api/batches/" + batch + "/split", split).andReturn(),
                () -> postAs(OPERATOR_OF_A, "/api/transfers", transfer).andReturn()));

        assertOneWinner(statuses, 201);
        assertThat(count("SELECT count(*) FROM transfers") + count("SELECT count(*) FROM batches WHERE origin_type = 'SPLIT'") / 2)
                .as("o hubo transferencia o hubo división, no las dos").isEqualTo(1);
    }

    @RepeatedTest(6)
    void acceptingAndRejectingTheSameTransferResolvesItOnce() throws Exception {
        UUID batch = idOf(registerBatch(100));
        String transfer = read(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(batch))))
                .path("id").asString();

        List<Integer> statuses = race(List.of(
                () -> postAs(ADMIN_OF_B, "/api/transfers/" + transfer + "/accept").andReturn(),
                () -> postAs(ADMIN_OF_B, "/api/transfers/" + transfer + "/reject", obj("reason", "No")).andReturn()));

        assertOneWinner(statuses, 200);
        assertThat(count("SELECT count(*) FROM transfers WHERE status <> 'PENDING'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_log WHERE action IN ('TRANSFER_ACCEPTED', 'TRANSFER_REJECTED')")).isEqualTo(1);
    }

    @RepeatedTest(6)
    void completingTheSameTransformationTwiceCreatesOneSetOfOutputs() throws Exception {
        UUID machine = idOf(registerMachine("MQ-" + UUID.randomUUID().toString().substring(0, 8)));
        UUID batch = idOf(registerBatch(100));
        JsonNode started = startTransformation("SPINNING", machine, batch);
        var complete = obj("outputs", List.of(output("Hilo", 90)));
        String url = "/api/transformations/" + started.path("id").asString() + "/complete";

        List<Integer> statuses = race(List.of(() -> postAs(OPERATOR_OF_A, url, complete).andReturn(), () -> postAs(OPERATOR_OF_A, url, complete).andReturn()));

        assertOneWinner(statuses, 200);
        assertThat(count("SELECT count(*) FROM batches WHERE origin_type = 'TRANSFORMATION'")).isEqualTo(1);
    }

    @RepeatedTest(6)
    void twoMachinesWithTheSameCodeCreatedAtOnceYieldOneAnd409() throws Exception {
        var machine = obj("internalCode", "MQ-RACE", "name", "Cardadora", "type", "Cardado");
        jdbc.sql("DELETE FROM machine_status_history").update();
        jdbc.sql("DELETE FROM machines").update();

        List<Integer> statuses = race(List.of(() -> postAs(ADMIN_OF_A, "/api/machines", machine).andReturn(),
                () -> postAs(ADMIN_OF_A, "/api/machines", machine).andReturn(), () -> postAs(ADMIN_OF_A, "/api/machines", machine).andReturn()));

        assertOneWinner(statuses, 201);
        assertThat(count("SELECT count(*) FROM machines")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM machine_status_history")).as("el perdedor no deja historial").isEqualTo(1);
    }
}
