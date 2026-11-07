package com.nodotextil.trazatex.production.integration;

import com.nodotextil.trazatex.production.application.port.in.ApplyQualityStatusUseCase;
import com.nodotextil.trazatex.production.application.port.out.BatchRepositoryPort;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Concurrencia real (hilos contra PostgreSQL) del comando interno de Quality con <b>bloqueo optimista</b>. Un 409 es solo un rechazo
 * de negocio —Quality cancela el control cuando lo recibe—, así que ninguna carrera puede terminar en 409
 * {@code CONCURRENT_MODIFICATION}: la transacción se reintenta dentro y, si la carrera persiste, la respuesta es 503
 * ({@code TEMPORARILY_UNAVAILABLE}), que Quality reintenta con el mismo {@code requestId}. Los perdedores de una carrera de
 * negocio reciben el conflicto del dominio ({@code INVALID_PHASE_TRANSITION}).
 */
class PhaseTransitionConcurrencyIT extends ApiTestBase {

    @Autowired
    ApplyQualityStatusUseCase qualityStatus;
    @Autowired
    BatchRepositoryPort batchRepository;

    private record Response(int status, String code, String body) {
    }

    private List<Response> race(List<Callable<MvcResult>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CyclicBarrier start = new CyclicBarrier(tasks.size());
        try {
            List<Future<Response>> futures = new ArrayList<>();
            for (Callable<MvcResult> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    MvcResult result = task.call();
                    String body = result.getResponse().getContentAsString();
                    return new Response(result.getResponse().getStatus(),
                            body.contains("\"code\"") ? json.readTree(body).path("code").asString() : "", body);
                }));
            }
            List<Response> responses = new ArrayList<>();
            for (Future<Response> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    private List<Callable<MvcResult>> repeat(int times, Callable<MvcResult> task) {
        List<Callable<MvcResult>> tasks = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            tasks.add(task);
        }
        return tasks;
    }

    private Callable<MvcResult> command(UUID batch, Object body) {
        return () -> internalCommand(batch, body).andReturn();
    }

    private static void assertNeverARaceError(List<Response> responses) {
        assertThat(responses).as("respuestas: %s", responses)
                .noneMatch(r -> r.code().equals("CONCURRENT_MODIFICATION") || r.code().equals("CONFLICT") || r.status() >= 500);
    }

    private long phaseEvents(UUID batch) {
        return count("SELECT count(*) FROM outbox WHERE event_type = 'BatchPhaseChanged' AND aggregate_id = ?", batch.toString());
    }

    private long logRows(UUID batch) {
        return count("SELECT count(*) FROM phase_command_log WHERE batch_id = ?", batch);
    }

    // ---- comandos idénticos simultáneos ----

    @RepeatedTest(6)
    void eightIdenticalStartEvaluationsApplyOnceAndAnswerTheSameToAll() throws Exception {
        UUID batch = idOf(registerBatch(10));
        jdbc.sql("DELETE FROM outbox").update();

        List<Response> responses = race(repeat(8, command(batch, phaseCommand("START_EVALUATION", "ctl-1"))));

        assertThat(responses).as("respuestas: %s", responses).allMatch(r -> r.status() == 200);
        assertThat(responses.stream().map(r -> r.body()).map(this::parse).distinct()).as("todas devuelven lo mismo").hasSize(1);
        assertThat(parse(responses.get(0).body()).path("phase").asString()).isEqualTo("IN_QUALITY_CONTROL");
        assertThat(phaseEvents(batch)).as("un solo BatchPhaseChanged").isEqualTo(1);
        assertThat(logRows(batch)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'BATCH_PHASE_COMMAND'")).isEqualTo(1);
    }

    @RepeatedTest(6)
    void eightIdenticalReleasesApplyOnce() throws Exception {
        UUID batch = idOf(registerBatch(10));
        internalCommand(batch, phaseCommand("START_EVALUATION", "ctl-1")).andExpect(status().isOk());
        jdbc.sql("DELETE FROM outbox").update();

        List<Response> responses = race(repeat(8, command(batch, phaseCommand("RELEASE", "ctl-1", true, "Con falla"))));

        assertThat(responses).as("respuestas: %s", responses).allMatch(r -> r.status() == 200);
        assertThat(responses.stream().map(r -> parse(r.body()))).allMatch(b -> b.path("phase").asString().equals("AVAILABLE") && b.path("qualityHold").asBoolean());
        assertThat(phaseEvents(batch)).isEqualTo(1);
    }

    /** Sin cambio no hay escritura del lote ni conflicto de versión: la barrera es la clave primaria de phase_command_log. */
    @RepeatedTest(6)
    void eightIdenticalHoldsThatChangeNothingAreAllAnsweredAndRecordedOnce() throws Exception {
        UUID batch = idOf(registerBatch(10));
        jdbc.sql("UPDATE batches SET quality_hold = TRUE WHERE id = :id").param("id", batch).update();
        jdbc.sql("DELETE FROM outbox").update();

        List<Response> responses = race(repeat(8, command(batch, phaseCommand("HOLD", "f-1"))));

        assertThat(responses).as("respuestas: %s", responses).allMatch(r -> r.status() == 200);
        assertThat(phaseEvents(batch)).isZero();
        assertThat(logRows(batch)).isEqualTo(1);
    }

    @RepeatedTest(6)
    void eightHoldsWithDifferentRequestIdsOnAnUnheldBatchChangeItOnce() throws Exception {
        UUID batch = idOf(registerBatch(10));
        jdbc.sql("DELETE FROM outbox").update();
        List<Callable<MvcResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(command(batch, phaseCommand("HOLD", "falla-" + i)));
        }

        List<Response> responses = race(tasks);

        assertThat(responses).as("respuestas: %s", responses).allMatch(r -> r.status() == 200 && parse(r.body()).path("qualityHold").asBoolean());
        assertThat(phaseEvents(batch)).as("solo uno cambió la retención").isEqualTo(1);
        assertThat(logRows(batch)).as("pero los ocho comandos quedan registrados").isEqualTo(8);
    }

    // ---- carreras de negocio: el perdedor recibe el conflicto del dominio ----

    @RepeatedTest(6)
    void startEvaluationAndHoldRacingAreAlwaysConsistent() throws Exception {
        UUID batch = idOf(registerBatch(10));
        jdbc.sql("DELETE FROM outbox").update();

        List<Response> responses = race(List.of(command(batch, phaseCommand("START_EVALUATION", "ctl-1")),
                command(batch, phaseCommand("HOLD", "falla-1"))));

        assertNeverARaceError(responses);
        assertThat(responses.get(0).status()).as("START_EVALUATION no exige habilitado: siempre prospera").isEqualTo(200);
        Response hold = responses.get(1);
        if (hold.status() == 409) {
            assertThat(hold.code()).as("si llegó después, el lote ya estaba en control: conflicto del dominio").isEqualTo("INVALID_PHASE_TRANSITION");
            assertThat(phaseEvents(batch)).isEqualTo(1);
        } else {
            assertThat(hold.status()).isEqualTo(200);
            assertThat(phaseEvents(batch)).as("la retención y el inicio del control").isEqualTo(2);
        }
        assertThat(jdbc.sql("SELECT current_stage FROM batches WHERE id = :id").param("id", batch).query(String.class).single())
                .isEqualTo("IN_QUALITY_CONTROL");
    }

    @RepeatedTest(6)
    void startEvaluationAndASplitByTheUserOnlyOneWins() throws Exception {
        UUID batch = idOf(registerBatch(10));
        jdbc.sql("DELETE FROM outbox").update();

        List<Response> responses = race(List.of(command(batch, phaseCommand("START_EVALUATION", "ctl-1")),
                () -> postAs(OPERATOR_OF_A, "/api/batches/" + batch + "/split",
                        obj("children", List.of(obj("quantityKg", 4), obj("quantityKg", 6)))).andReturn()));

        assertNeverARaceError(responses);
        Response evaluation = responses.get(0);
        Response split = responses.get(1);
        assertThat(evaluation.status() == 200 ^ split.status() == 201).as("respuestas: %s", responses).isTrue();
        if (evaluation.status() == 200) {
            assertThat(split.status()).isEqualTo(409);
            assertThat(split.code()).isEqualTo("INVALID_PHASE_TRANSITION");
        } else {
            assertThat(evaluation.status()).isEqualTo(409);
            assertThat(evaluation.code()).isEqualTo("INVALID_PHASE_TRANSITION");
        }
        assertThat(phaseEvents(batch)).as("un solo cambio de fase del lote").isEqualTo(1);
    }

    @RepeatedTest(6)
    void startEvaluationAndATransferByTheUserOnlyOneWins() throws Exception {
        UUID batch = idOf(registerBatch(10));
        jdbc.sql("DELETE FROM outbox").update();

        List<Response> responses = race(List.of(command(batch, phaseCommand("START_EVALUATION", "ctl-1")),
                () -> postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(batch))).andReturn()));

        assertNeverARaceError(responses);
        assertThat(responses.get(0).status() == 200 ^ responses.get(1).status() == 201).as("respuestas: %s", responses).isTrue();
        Response loser = responses.get(0).status() == 200 ? responses.get(1) : responses.get(0);
        assertThat(loser.status()).isEqualTo(409);
        assertThat(loser.code()).isEqualTo("INVALID_PHASE_TRANSITION");
        assertThat(phaseEvents(batch)).isEqualTo(1);
    }

    // ---- contra QualityStatusChanged (que lee sin bloqueo y guarda con @Version) ----

    /**
     * Presión máxima sobre una sola fila: 12 comandos que SÍ cambian el lote más un flujo de 40 actualizaciones de calidad. Un
     * comando puede perder todas las rondas y recibir 503 (nunca 409 ni 500): entonces Quality repite el comando con el mismo
     * requestId, y aquí se hace igual. Lo que no puede pasar es una respuesta que no sea 200 o 503, ni un evento que no sea un cambio real.
     */
    @Test
    void commandsRacingWithQualityStatusUpdatesEndInAnswersQualityCanActOnAndEveryEventIsARealChange() throws Exception {
        UUID batch = idOf(registerBatch(10));
        jdbc.sql("DELETE FROM outbox").update();
        ExecutorService consumer = Executors.newSingleThreadExecutor();
        CountDownLatch go = new CountDownLatch(1);
        Future<?> updates = consumer.submit(() -> {
            go.await();
            QualityStatus[] statuses = QualityStatus.values();
            for (int i = 0; i < 40; i++) {
                for (int attempt = 0; attempt < 20; attempt++) {
                    try {
                        qualityStatus.apply(batch, COMPANY_A, statuses[i % statuses.length], UUID.randomUUID());
                        break;
                    } catch (org.springframework.dao.OptimisticLockingFailureException conflict) {
                        // el consumidor real reintenta con espera creciente
                    }
                }
            }
            return null;
        });
        List<Callable<MvcResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String requestId = "k-" + i;
            Object body = phaseCommand(i % 2 == 0 ? "HOLD" : "RELEASE_HOLD", requestId);
            tasks.add(() -> {
                // Quality: con 503 repite el mismo comando (mismo requestId)
                MvcResult result = internalCommand(batch, body).andReturn();
                for (int retry = 0; retry < 20 && result.getResponse().getStatus() == 503; retry++) {
                    assertThat(result.getResponse().getHeader("Retry-After")).isEqualTo("1");
                    Thread.sleep(25);
                    result = internalCommand(batch, body).andReturn();
                }
                return result;
            });
        }
        go.countDown();

        List<Response> responses = race(tasks);
        updates.get(30, TimeUnit.SECONDS);
        consumer.shutdownNow();

        assertThat(responses).as("respuestas: %s", responses).allMatch(r -> r.status() == 200);
        List<Boolean> holds = jdbc.sql("SELECT (payload->'payload'->>'qualityHold')::boolean FROM outbox WHERE event_type = 'BatchPhaseChanged' ORDER BY created_at")
                .query(Boolean.class).list();
        for (int i = 1; i < holds.size(); i++) {
            assertThat(holds.get(i)).as("cada evento es un cambio real, nunca dos iguales seguidos: %s", holds).isNotEqualTo(holds.get(i - 1));
        }
        boolean finalHold = jdbc.sql("SELECT quality_hold FROM batches WHERE id = :id").param("id", batch).query(Boolean.class).single();
        assertThat(finalHold).as("el estado final es el del último cambio real").isEqualTo(!holds.isEmpty() && holds.get(holds.size() - 1));
        assertThat(logRows(batch)).as("los 12 comandos quedan registrados, cada uno una vez").isEqualTo(12);
    }

    // ---- una operación del usuario con el lote en FOR UPDATE ----

    @Test
    void aLockHeldByAUserOperationMakesTheCommandAnswer503BeforeQualityGivesUpAndTheSameRequestIdWorksAfterwards() throws Exception {
        UUID batch = idOf(registerBatch(10));
        jdbc.sql("DELETE FROM outbox").update();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService holder = Executors.newSingleThreadExecutor();
        Future<?> holding = holder.submit(() -> tx().executeWithoutResult(status -> {
            jdbc.sql("SELECT id FROM batches WHERE id = :id FOR UPDATE").param("id", batch).query(UUID.class).single();
            locked.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        long startedAt = System.nanoTime();
        MvcResult blocked = internalCommand(batch, phaseCommand("START_EVALUATION", "ctl-1")).andReturn();
        Duration waited = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(blocked.getResponse().getStatus()).isEqualTo(503);
        assertThat(blocked.getResponse().getHeader("Retry-After")).isEqualTo("1");
        assertThat(json.readTree(blocked.getResponse().getContentAsString()).path("code").asString()).isEqualTo("TEMPORARILY_UNAVAILABLE");
        assertThat(waited).as("responde antes del timeout de 2 s de Quality").isLessThan(Duration.ofMillis(2_000)).isGreaterThan(Duration.ofMillis(1_000));
        assertThat(count("SELECT count(*) FROM outbox")).isZero();

        release.countDown();
        holding.get(10, TimeUnit.SECONDS);
        holder.shutdownNow();
        JsonNode retried = read(internalCommand(batch, phaseCommand("START_EVALUATION", "ctl-1")).andExpect(status().isOk()));
        assertThat(retried.path("phase").asString()).isEqualTo("IN_QUALITY_CONTROL");
        assertThat(phaseEvents(batch)).isEqualTo(1);
    }

    private JsonNode parse(String body) {
        return json.readTree(body);
    }
}
