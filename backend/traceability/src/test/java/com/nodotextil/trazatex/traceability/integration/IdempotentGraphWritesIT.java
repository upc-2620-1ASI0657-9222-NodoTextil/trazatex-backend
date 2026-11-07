package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxEventRecorder;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.sharedmessaging.consumer.ProcessedEventPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR-004 sobre Neo4j: el {@code :ProcessedEvent}, el cambio del grafo y el {@code :OutboxEvent} comparten UNA transacción
 * ({@code IdempotentEventHandler} + {@code Neo4jTransactionManager}): se confirman o se revierten juntos.
 */
class IdempotentGraphWritesIT extends IntegrationTestBase {

    @Autowired
    IdempotentEventHandler handler;
    @Autowired
    ProcessedEventPort processedEvents;
    @Autowired
    OutboxEventRecorder recorder;
    @Autowired
    PlatformTransactionManager transactionManager;

    TransactionTemplate tx;

    @BeforeEach
    void template() {
        tx = new TransactionTemplate(transactionManager);
    }

    private long count(String label) {
        return (Long) neo4j.query("MATCH (n:" + label + ") RETURN count(n) AS n").fetch().one().orElseThrow().get("n");
    }

    private BaseDomainEvent event() {
        return BaseDomainEvent.of("BatchRegistered", UUID.randomUUID().toString(), Map.of("x", 1));
    }

    /** El efecto de un consumidor de verdad: cambia el grafo y deja un evento en el outbox. */
    private void effect(BaseDomainEvent event) {
        neo4j.query("MERGE (b:Batch {batchId: $id}) ON CREATE SET b.provisional = true").bind(event.aggregateId()).to("id").run();
        recorder.record("Batch", "traceability.impact", BaseDomainEvent.of("PossibleDerivedFailureDetected", event.aggregateId(), Map.of("ok", true)));
    }

    private boolean mark(UUID id) {
        return Boolean.TRUE.equals(tx.execute(s -> processedEvents.markProcessed(id, "BatchRegistered")));
    }

    // ---- ProcessedEventPort ----

    @Test
    void anEventIsMarkedOnlyTheFirstTime() {
        UUID id = UUID.randomUUID();

        assertThat(mark(id)).isTrue();
        assertThat(mark(id)).isFalse();
        assertThat(count("ProcessedEvent")).isEqualTo(1);
    }

    @Test
    void eightTransactionsMarkingTheSameEventAtOnceLetExactlyOneWin() throws Exception {
        UUID id = UUID.randomUUID();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier start = new CyclicBarrier(threads);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return mark(id);
                }));
            }
            int winners = 0;
            for (Future<Boolean> future : futures) {
                winners += future.get(60, TimeUnit.SECONDS) ? 1 : 0;
            }
            assertThat(winners).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("ProcessedEvent")).isEqualTo(1);
    }

    // ---- una sola transacción: grafo + ProcessedEvent + OutboxEvent ----

    @Test
    void theGraphChangeTheProcessedEventAndTheOutboxEventAreCommittedTogether() {
        BaseDomainEvent event = event();

        assertThat(handler.handle(event, this::effect)).isTrue();

        assertThat(count("Batch")).isEqualTo(1);
        assertThat(count("ProcessedEvent")).isEqualTo(1);
        assertThat(neo4j.query("MATCH (o:OutboxEvent) RETURN o.status AS status, o.topic AS topic, o.attempts AS attempts").fetch().one().orElseThrow())
                .containsEntry("status", "PENDING").containsEntry("topic", "traceability.impact").containsEntry("attempts", 0L);
    }

    @Test
    void ifTheEffectFailsNothingIsLeftBehindAndTheRetrySucceeds() {
        BaseDomainEvent event = event();

        assertThatThrownBy(() -> handler.handle(event, e -> {
            effect(e);
            throw new IllegalStateException("falla después de escribir el grafo y el outbox");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(count("Batch")).as("el grafo se revirtió").isZero();
        assertThat(count("OutboxEvent")).as("el outbox se revirtió").isZero();
        assertThat(count("ProcessedEvent")).as("el registro del evento también: el reintento lo vuelve a intentar").isZero();

        assertThat(handler.handle(event, this::effect)).isTrue();
        assertThat(count("Batch")).isEqualTo(1);
        assertThat(count("OutboxEvent")).isEqualTo(1);
        assertThat(count("ProcessedEvent")).isEqualTo(1);
    }

    @Test
    void aDuplicateEventDoesNotRunTheEffectAgain() {
        BaseDomainEvent event = event();
        AtomicInteger runs = new AtomicInteger();

        assertThat(handler.handle(event, e -> {
            runs.incrementAndGet();
            effect(e);
        })).isTrue();
        assertThat(handler.handle(event, e -> {
            runs.incrementAndGet();
            effect(e);
        })).isFalse();

        assertThat(runs).hasValue(1);
        assertThat(count("OutboxEvent")).isEqualTo(1);
        assertThat(count("ProcessedEvent")).isEqualTo(1);
    }

    @Test
    void sixConsumersReceivingTheSameEventAtOnceApplyItExactlyOnce() throws Exception {
        BaseDomainEvent event = event();
        AtomicInteger runs = new AtomicInteger();
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier start = new CyclicBarrier(threads);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return handler.handle(event, e -> {
                        runs.incrementAndGet();
                        effect(e);
                    });
                }));
            }
            for (Future<Boolean> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(runs).hasValue(1);
        assertThat(count("OutboxEvent")).isEqualTo(1);
        assertThat(count("ProcessedEvent")).isEqualTo(1);
    }

    @Test
    void theOutboxCanOnlyBeWrittenInsideTheTransactionOfTheUseCase() {
        assertThatThrownBy(() -> recorder.record("Batch", "traceability.impact", event())).isInstanceOf(IllegalStateException.class);
        assertThat(count("OutboxEvent")).isZero();
    }
}
