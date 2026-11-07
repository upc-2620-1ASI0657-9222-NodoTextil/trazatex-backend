package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchPhaseChangedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchSplitUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyPrivacyUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyQualityStatusUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransferAcceptedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransformationCompletedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.RegisterBatchUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El grafo final es el mismo en cualquier orden de llegada y con eventos repetidos (docs/domain/traceability.md, "Orden de
 * llegada"). Kafka solo conserva el orden de una clave dentro de un tópico, así que cada (tópico, clave) es un flujo ordenado y
 * los flujos se mezclan sin orden entre sí: se recorren <b>todas</b> las mezclas del caso pequeño y una muestra de semilla fija
 * del grande, con reentregas intercaladas, y cada resultado se compara con el grafo escrito a mano.
 */
class LineageOrderIT extends IntegrationTestBase {

    /** Semilla fija: cualquier fallo se reproduce. */
    private static final long SEED = 20261021L;
    private static final int WORKERS = 4;
    private static final int LARGE_SAMPLES = Integer.getInteger("trazatex.order.large-samples", 1500);
    private static final int FREE_SAMPLES = Integer.getInteger("trazatex.order.free-samples", 2000);
    /** Para medir sin esperar toda la corrida: 0 = todas las mezclas. */
    private static final int EXHAUSTIVE_LIMIT = Integer.getInteger("trazatex.order.exhaustive-limit", 0);

    @Autowired
    IdempotentEventHandler handler;
    @Autowired
    RegisterBatchUseCase register;
    @Autowired
    ApplyBatchSplitUseCase split;
    @Autowired
    ApplyTransformationCompletedUseCase transformation;
    @Autowired
    ApplyBatchPhaseChangedUseCase phase;
    @Autowired
    ApplyTransferAcceptedUseCase transfer;
    @Autowired
    ApplyQualityStatusUseCase quality;
    @Autowired
    ApplyCompanyPrivacyUseCase privacy;

    OrderScenario.Beans beans;

    @BeforeEach
    void beans() {
        beans = new OrderScenario.Beans(handler, register, split, transformation, phase, transfer, quality, privacy);
    }

    // ---- una corrida ----

    private record Failure(int run, String order, String problem) {
    }

    /** Aplica los eventos en ese orden y compara el grafo con el esperado; limpia sus nodos al terminar. Nunca lanza. */
    private Failure runOnce(int run, OrderScenario.Instance instance, int[] order) {
        try {
            for (int index : order) {
                instance.events().get(index).deliver().run();
            }
            List<String> actual = GraphSnapshot.of(neo4j, instance.entityIds(), instance.names());
            List<String> missing = new ArrayList<>(instance.expected());
            actual.forEach(missing::remove);
            List<String> unexpected = new ArrayList<>(actual);
            instance.expected().forEach(unexpected::remove);
            long processed = (Long) neo4j.query("MATCH (p:ProcessedEvent) WHERE p.eventId IN $ids RETURN count(p) AS n")
                    .bind(instance.eventIds().stream().map(Object::toString).toList()).to("ids").fetch().one().orElseThrow().get("n");
            if (!missing.isEmpty() || !unexpected.isEmpty() || processed != instance.events().size()) {
                return new Failure(run, instance.describe(order), "faltan " + missing + "\n  sobran " + unexpected + "\n  eventos procesados " + processed
                        + " de " + instance.events().size());
            }
            return null;
        } catch (Throwable e) {
            return new Failure(run, instance.describe(order), e.toString());
        } finally {
            clean(instance);
        }
    }

    private void clean(OrderScenario.Instance instance) {
        List<String> entities = instance.entityIds().stream().map(Object::toString).toList();
        neo4j.query("MATCH (b:Batch) WHERE b.batchId IN $ids DETACH DELETE b").bind(entities).to("ids").run();
        neo4j.query("MATCH (t:Transformation) WHERE t.transformationId IN $ids DETACH DELETE t").bind(entities).to("ids").run();
        neo4j.query("MATCH (c:Company) WHERE c.companyId IN $ids DETACH DELETE c").bind(entities).to("ids").run();
        neo4j.query("MATCH (p:ProcessedEvent) WHERE p.eventId IN $ids DETACH DELETE p")
                .bind(instance.eventIds().stream().map(Object::toString).toList()).to("ids").run();
    }

    /** Ejecuta las corridas en paralelo (cada una con ids propios) y falla mostrando las primeras que no den el grafo esperado. */
    private void runAll(String title, int runs, IntFunction<OrderScenario.Instance> scenario, IntFunction<int[]> orderOf) throws Exception {
        long start = System.nanoTime();
        Queue<Failure> failures = new ConcurrentLinkedQueue<>();
        AtomicInteger next = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
        try {
            List<Future<?>> workers = new ArrayList<>();
            for (int worker = 0; worker < WORKERS; worker++) {
                workers.add(pool.submit(() -> {
                    int run;
                    while ((run = next.getAndIncrement()) < runs && failures.size() < 10) {
                        Failure failure = runOnce(run, scenario.apply(run), orderOf.apply(run));
                        if (failure != null) {
                            failures.add(failure);
                        }
                    }
                    return null;
                }));
            }
            for (Future<?> worker : workers) {
                worker.get(30, TimeUnit.MINUTES);
            }
        } finally {
            pool.shutdownNow();
        }
        long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);
        System.out.printf("ORDEN %s: %d corridas en %d s con %d hilos%n", title, runs, seconds, WORKERS);
        assertThat(failures).as(title + ": mezclas que no dan el grafo esperado (primeras 5)")
                .extracting(failure -> "corrida " + failure.run() + ": " + failure.order() + "\n  " + failure.problem()).isEmpty();
    }

    // ---- el caso pequeño: TODAS las mezclas ----

    @Test
    void everyInterleavingOfTheSmallCaseGivesTheSameGraphEvenWithRepeatedDeliveries() throws Exception {
        OrderScenario.Instance sample = OrderScenario.small(0, beans);
        List<int[]> all = Interleavings.all(sample.streamLengths());
        int total = EXHAUSTIVE_LIMIT > 0 ? Math.min(EXHAUSTIVE_LIMIT, all.size()) : all.size();
        System.out.printf("ORDEN caso pequeño: %d eventos, %d flujos, %d mezclas posibles, se recorren %d%n",
                sample.events().size(), sample.streamLengths().length, all.size(), total);
        runAll("caso pequeño, todas las mezclas con 3 reentregas", total, run -> OrderScenario.small(run, beans),
                run -> Interleavings.withDuplicates(all.get(run), 3, Interleavings.seeded(SEED, run)));
    }

    @Test
    void theSmallCaseHasTheNumberOfInterleavingsTheFlowsImply() {
        OrderScenario.Instance sample = OrderScenario.small(0, beans);

        // 8! / (3! 2! 1! 1! 1!) = 3360
        assertThat(sample.streamNames()).containsExactly("batch/L1", "batch/H1", "batch/H2", "transformation/T1", "transfer/TR1");
        assertThat(sample.streamLengths()).containsExactly(3, 2, 1, 1, 1);
        assertThat(Interleavings.all(sample.streamLengths())).hasSize(3360).doesNotHaveDuplicates();
    }

    // ---- el caso grande: muestra de semilla fija ----

    @Test
    void randomInterleavingsOfTheLargeCaseGiveTheSameGraphEvenWithRepeatedDeliveries() throws Exception {
        OrderScenario.Instance sample = OrderScenario.large(0, beans);
        System.out.printf("ORDEN caso grande: %d eventos en %d flujos%n", sample.events().size(), sample.streamLengths().length);
        runAll("caso grande, mezclas al azar con 4 reentregas", LARGE_SAMPLES, run -> OrderScenario.large(run, beans), run -> {
            Random random = Interleavings.seeded(SEED * 31, run);
            return Interleavings.withDuplicates(Interleavings.random(sample.streamLengths(), random), 4, random);
        });
    }

    // ---- adicional: sin respetar ni el orden de cada flujo ----

    @Test
    void evenFreePermutationsThatBreakTheOrderOfEachStreamGiveTheSameGraphOfTheSmallCase() throws Exception {
        OrderScenario.Instance sample = OrderScenario.small(0, beans);
        runAll("caso pequeño, permutaciones libres con 2 reentregas", FREE_SAMPLES, run -> OrderScenario.small(run, beans), run -> {
            Random random = Interleavings.seeded(SEED * 17, run);
            return Interleavings.withDuplicates(Interleavings.freePermutation(sample.events().size(), random), 2, random);
        });
    }

    @Test
    void theSampledOrdersAreVariedEnoughToExerciseTheOrderRules() {
        OrderScenario.Instance sample = OrderScenario.small(0, beans);
        int n = sample.events().size();
        int newerBeforeOlder = 0;
        java.util.Set<String> distinct = new java.util.HashSet<>();
        for (int run = 0; run < 400; run++) {
            int[] order = Interleavings.freePermutation(n, Interleavings.seeded(SEED * 17, run));
            distinct.add(java.util.Arrays.toString(order));
            List<Integer> positions = java.util.Arrays.stream(order).boxed().toList();
            // eventos 3 y 4 = los dos cambios de fase de H1: v1 y v2
            if (positions.indexOf(4) < positions.indexOf(3)) {
                newerBeforeOlder++;
            }
        }
        assertThat(distinct.size()).isGreaterThan(380);
        assertThat(newerBeforeOlder).as("la versión nueva antes que la vieja debe ocurrir en cerca de la mitad de las corridas")
                .isBetween(140, 260);
    }

    // ---- órdenes con nombre: los casos difíciles, legibles ----

    private void assertOrder(String... eventNames) {
        OrderScenario.Instance instance = OrderScenario.small(900_000 + Math.abs(String.join("|", eventNames).hashCode() % 90_000), beans);
        List<String> byName = instance.events().stream().map(OrderScenario.Ev::name).toList();
        int[] order = java.util.Arrays.stream(eventNames).mapToInt(name -> {
            assertThat(byName).contains(name);
            return byName.indexOf(name);
        }).toArray();
        assertThat(order).hasSize(instance.events().size());
        Failure failure = runOnce(0, instance, order);
        assertThat(failure == null ? "" : failure.order() + "\n  " + failure.problem()).isEmpty();
    }

    @Test
    void namedOrders() {
        OrderScenario.NAMED_ORDERS.forEach(order -> assertOrder(order.toArray(String[]::new)));
    }

    @Test
    void aStalePhaseChangeRedeliveredAfterTheNewerOneChangesNothing() {
        OrderScenario.Instance instance = OrderScenario.small(910_000, beans);
        List<String> byName = instance.events().stream().map(OrderScenario.Ev::name).toList();
        int v1 = byName.indexOf("BPC(H1 IN_TRANSFORMATION v1)");
        int v2 = byName.indexOf("BPC(H1 PROCESSED v2)");
        int[] order = {0, 1, 2, v1, v2, byName.indexOf("TC(T1)"), byName.indexOf("BPC(H2 AVAILABLE cB v2)"), byName.indexOf("TA(TR1>cB)"), v1, v1, v2};

        Failure failure = runOnce(0, instance, order);

        assertThat(failure == null ? "" : failure.order() + "\n  " + failure.problem()).isEmpty();
    }
}
