package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedkernel.outbox.EventPublishException;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxEventRecorder;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxMessage;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxRelay;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxStorePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** El outbox de nodos {@code :OutboxEvent} y el {@code OutboxRelay} del shared-kernel, contra Neo4j real. */
class TransactionalOutboxIT extends IntegrationTestBase {

    @Autowired
    OutboxStorePort store;
    @Autowired
    OutboxEventRecorder recorder;
    @Autowired
    PlatformTransactionManager transactionManager;

    TransactionTemplate tx;
    List<String> published;

    @BeforeEach
    void prepare() {
        tx = new TransactionTemplate(transactionManager);
        published = Collections.synchronizedList(new ArrayList<>());
    }

    private List<OutboxMessage> pending(int limit) {
        return tx.execute(s -> store.findPending(limit));
    }

    private OutboxRelay relay() {
        return new OutboxRelay(store, (topic, key, payload) -> published.add(topic + "|" + key + "|" + payload), transactionManager);
    }

    private static OutboxMessage message(UUID id, String aggregate, Instant createdAt) {
        return new OutboxMessage(id, "Failure", aggregate, "PossibleDerivedFailureDetected", "traceability.impact", "{\"n\":\"" + id + "\"}", createdAt);
    }

    private Map<String, Object> node(UUID id) {
        return neo4j.query("MATCH (o:OutboxEvent {eventId: $id}) RETURN o.status AS status, o.attempts AS attempts, o.lastError AS lastError, "
                + "o.publishedAt AS publishedAt, o.createdAt AS createdAt").bind(id.toString()).to("id").fetch().one().orElseThrow();
    }

    // ---- el almacén ----

    @Test
    void pendingMessagesComeOldestFirstWithTheirFieldsAndTheLimitIsRespected() {
        Instant t0 = Instant.parse("2026-10-21T10:00:00.000001Z");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        tx.executeWithoutResult(s -> {
            store.save(message(second, "f-1", t0.plusNanos(2000)));
            store.save(message(third, "f-1", t0.plusNanos(3000)));
            store.save(message(first, "f-1", t0.plusNanos(1000)));
        });

        List<OutboxMessage> pending = pending(2);

        assertThat(pending).extracting(OutboxMessage::id).containsExactly(first, second);
        assertThat(pending.get(0)).isEqualTo(message(first, "f-1", t0.plusNanos(1000)));
        assertThat(pending(10)).hasSize(3);
    }

    @Test
    void aPublishedMessageIsNoLongerPendingAndKeepsItsHistory() {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(s -> store.save(message(id, "f-1", Instant.parse("2026-10-21T10:00:00Z"))));

        tx.executeWithoutResult(s -> store.markPublished(id, Instant.parse("2026-10-21T10:00:05Z")));

        assertThat(pending(10)).isEmpty();
        assertThat(node(id)).containsEntry("status", "PUBLISHED").containsKey("publishedAt");
    }

    @Test
    void aFailedAttemptCountsAndKeepsTheErrorButTheMessageStaysPending() {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(s -> store.save(message(id, "f-1", Instant.parse("2026-10-21T10:00:00Z"))));

        tx.executeWithoutResult(s -> store.markFailed(id, "KafkaException: broker caído"));
        tx.executeWithoutResult(s -> store.markFailed(id, "KafkaException: otra vez"));

        assertThat(node(id)).containsEntry("status", "PENDING").containsEntry("attempts", 2L).containsEntry("lastError", "KafkaException: otra vez");
        assertThat(pending(10)).extracting(OutboxMessage::id).containsExactly(id);
    }

    @Test
    void theSameEventIdCannotBeSavedTwice() {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(s -> store.save(message(id, "f-1", Instant.parse("2026-10-21T10:00:00Z"))));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> tx.executeWithoutResult(s -> store.save(message(id, "f-1", Instant.parse("2026-10-21T10:00:01Z")))))
                .hasMessageContaining("already exists");
    }

    // ---- el relay ----

    @Test
    void theRelayPublishesInOrderWithTheAggregateAsKeyAndMarksEachOne() {
        tx.executeWithoutResult(s -> {
            recorder.record("Failure", "traceability.impact", BaseDomainEvent.of("PossibleDerivedFailureDetected", "f-1", Map.of("n", 1)));
            recorder.record("Failure", "traceability.impact", BaseDomainEvent.of("PossibleDerivedFailureDetected", "f-2", Map.of("n", 2)));
            recorder.record("Failure", "traceability.impact", BaseDomainEvent.of("PossibleDerivedFailureDetected", "f-1", Map.of("n", 3)));
        });

        OutboxRelay.Result result = relay().publishPending();

        assertThat(result.published()).isEqualTo(3);
        assertThat(published).extracting(line -> line.split("\\|")[1]).containsExactly("f-1", "f-2", "f-1");
        assertThat(published.get(0)).startsWith("traceability.impact|f-1|").contains("\"n\":1");
        assertThat(pending(10)).isEmpty();
        assertThat(relay().publishPending().published()).isZero();
    }

    @Test
    void aFailingBrokerLeavesTheMessagePendingWithItsErrorAndTheNextPassRetriesIt() {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(s -> store.save(message(id, "f-1", Instant.parse("2026-10-21T10:00:00Z"))));
        OutboxRelay failing = new OutboxRelay(store, (topic, key, payload) -> {
            throw new EventPublishException("sin broker", null);
        }, transactionManager);

        OutboxRelay.Result result = failing.publishPending();

        assertThat(result.failed()).isEqualTo(1);
        assertThat(node(id)).containsEntry("status", "PENDING").containsEntry("attempts", 1L);
        assertThat((String) node(id).get("lastError")).contains("sin broker");
        assertThat(relay().publishPending().published()).isEqualTo(1);
        assertThat(node(id)).containsEntry("status", "PUBLISHED").containsEntry("attempts", 1L);
    }

    @Test
    void nothingIsPublishedBeforeTheCommitAndNothingEverIfTheTransactionRollsBack() throws Exception {
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            tx.executeWithoutResult(s -> {
                recorder.record("Failure", "traceability.impact", BaseDomainEvent.of("PossibleDerivedFailureDetected", "f-1", Map.of("n", 1)));
                // el relay corre en otro hilo (otra transacción) con la del caso de uso todavía abierta
                try {
                    assertThat(other.submit(() -> relay().publishPending().published()).get(30, TimeUnit.SECONDS)).isZero();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            assertThat(relay().publishPending().published()).as("tras el commit sí").isEqualTo(1);

            published.clear();
            tx.executeWithoutResult(s -> {
                recorder.record("Failure", "traceability.impact", BaseDomainEvent.of("PossibleDerivedFailureDetected", "f-2", Map.of("n", 2)));
                s.setRollbackOnly();
            });
            assertThat(relay().publishPending().published()).as("con rollback nunca").isZero();
            assertThat(published).isEmpty();
        } finally {
            other.shutdownNow();
        }
    }

    @Test
    void twoRelaysAtTheSameTimeNeverPublishTheSameMessageTwice() throws Exception {
        int total = 40;
        tx.executeWithoutResult(s -> {
            for (int i = 0; i < total; i++) {
                recorder.record("Failure", "traceability.impact", BaseDomainEvent.of("PossibleDerivedFailureDetected", "f-" + (i % 4), Map.of("n", i)));
            }
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return new OutboxRelay(store, (topic, key, payload) -> published.add(payload), transactionManager,
                            java.time.Clock.systemUTC(), 15).publishPending().published();
                }));
            }
            int sum = 0;
            for (Future<Integer> future : futures) {
                sum += future.get(90, TimeUnit.SECONDS);
            }
            assertThat(sum).as("entre los dos relays, en una pasada cada uno").isLessThanOrEqualTo(total);
        } finally {
            pool.shutdownNow();
        }
        // vaciar lo que quede con pasadas sucesivas
        while (relay().publishPending().published() > 0) {
            // sigue hasta que no quede nada pendiente
        }

        assertThat(published).as("cada mensaje exactamente una vez").hasSize(total).doesNotHaveDuplicates();
        assertThat(pending(100)).isEmpty();
    }
}
