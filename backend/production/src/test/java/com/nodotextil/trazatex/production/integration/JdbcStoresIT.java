package com.nodotextil.trazatex.production.integration;

import com.nodotextil.trazatex.production.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.sharedkernel.idempotency.IdempotencyRecord;
import com.nodotextil.trazatex.sharedkernel.idempotency.IdempotencyStorePort;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxMessage;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxStorePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El contrato SQL de los puertos del shared-kernel contra PostgreSQL real (deuda de la fase 01):
 * {@code ON CONFLICT DO NOTHING} con bloqueo de la PK y {@code FOR UPDATE SKIP LOCKED}.
 */
class JdbcStoresIT extends IntegrationTestBase {

    @Autowired
    IdempotencyStorePort idempotency;
    @Autowired
    OutboxStorePort outbox;
    @Autowired
    AuditLogPort audit;


    // ---- idempotency_keys ----

    @Test
    void reserveCompleteFindAndDelete() {
        String key = UUID.randomUUID().toString();

        Boolean firstReservation = tx().execute(s -> idempotency.tryReserve(key, "user-1", "hash-1", START));
        Boolean secondReservation = tx().execute(s -> idempotency.tryReserve(key, "user-1", "hash-1", START));
        assertThat(firstReservation).isTrue();
        assertThat(secondReservation).isFalse();
        assertThat(idempotency.find(key)).get().extracting(IdempotencyRecord::responseStatus).isEqualTo(0);

        tx().executeWithoutResult(s -> idempotency.complete(key, 201, "{\"id\":\"abc\"}"));
        IdempotencyRecord stored = idempotency.find(key).orElseThrow();
        assertThat(stored.responseStatus()).isEqualTo(201);
        assertThat(stored.responseBody()).contains("\"id\"").contains("abc");
        assertThat(stored.userId()).isEqualTo("user-1");
        assertThat(stored.requestHash()).isEqualTo("hash-1");
        assertThat(stored.createdAt()).isEqualTo(START);

        tx().executeWithoutResult(s -> idempotency.complete(key, 204, null));
        assertThat(idempotency.find(key).orElseThrow().responseBody()).isNull();

        tx().executeWithoutResult(s -> idempotency.delete(key));
        assertThat(idempotency.find(key)).isEmpty();
    }

    @Test
    void deleteCreatedBeforeOnlyRemovesOldKeys() {
        tx().executeWithoutResult(s -> {
            idempotency.tryReserve("old", "u", "h", START.minus(Duration.ofHours(30)));
            idempotency.tryReserve("recent", "u", "h", START.minus(Duration.ofHours(1)));
        });

        int deleted = tx().execute(s -> idempotency.deleteCreatedBefore(START.minus(Duration.ofHours(24))));

        assertThat(deleted).isEqualTo(1);
        assertThat(idempotency.find("old")).isEmpty();
        assertThat(idempotency.find("recent")).isPresent();
    }

    @Test
    void aSecondReservationOfTheSameKeyWaitsForTheFirstTransactionAndThenLoses() throws Exception {
        String key = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch reserved = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Future<Boolean> first = pool.submit(() -> tx().execute(s -> {
                boolean result = idempotency.tryReserve(key, "u", "h", START);
                reserved.countDown();
                await(release);
                return result;
            }));
            assertThat(reserved.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> second = pool.submit(() -> tx().execute(s -> idempotency.tryReserve(key, "u", "h", START)));

            Thread.sleep(500);
            assertThat(second.isDone()).as("la PK debe bloquear al segundo hasta que el primero confirme").isFalse();
            release.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isFalse();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void ifTheFirstTransactionRollsBackTheSecondGetsTheKey() throws Exception {
        String key = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch reserved = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Future<Boolean> first = pool.submit(() -> tx().execute(s -> {
                boolean result = idempotency.tryReserve(key, "u", "h", START);
                reserved.countDown();
                await(release);
                s.setRollbackOnly();
                return result;
            }));
            assertThat(reserved.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> second = pool.submit(() -> tx().execute(s -> idempotency.tryReserve(key, "u", "h", START)));
            Thread.sleep(300);
            release.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- audit_log (RNF-11) ----

    @Test
    void auditRecordsWhoWhatAndWhenInTheSameTransaction() {
        UUID user = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        clock.advance(Duration.ofMinutes(5));

        tx().executeWithoutResult(s -> audit.record(user, company, "BATCH_REGISTERED", "Batch", "batch-1",
                java.util.Map.of("quantityKg", "250.500", "materialType", "Fibra")));

        var row = jdbc.sql("SELECT user_id, company_id, action, entity_type, entity_id, changes::text AS changes, occurred_at FROM audit_log")
                .query((rs, n) -> java.util.List.of(rs.getObject("user_id", UUID.class), rs.getObject("company_id", UUID.class),
                        rs.getString("action"), rs.getString("entity_type"), rs.getString("entity_id"), rs.getString("changes"),
                        rs.getObject("occurred_at", java.time.OffsetDateTime.class).toInstant()))
                .single();
        assertThat(row.get(0)).isEqualTo(user);
        assertThat(row.get(1)).isEqualTo(company);
        assertThat(row.subList(2, 5)).containsExactly("BATCH_REGISTERED", "Batch", "batch-1");
        assertThat((String) row.get(5)).contains("250.500").contains("Fibra");
        assertThat(row.get(6)).isEqualTo(START.plus(Duration.ofMinutes(5)));
    }

    @Test
    void anAuditRowWithoutChangesOrCompanyIsAccepted() {
        tx().executeWithoutResult(s -> audit.record(UUID.randomUUID(), null, "MACHINE_REGISTERED", "Machine", "m-1", null));

        assertThat(count("SELECT count(*) FROM audit_log WHERE changes IS NULL AND company_id IS NULL")).isEqualTo(1);
    }

    @Test
    void anAuditRowRolledBackWithTheOperationLeavesNoTrace() {
        tx().executeWithoutResult(s -> {
            audit.record(UUID.randomUUID(), UUID.randomUUID(), "BATCH_SPLIT", "Batch", "x", null);
            s.setRollbackOnly();
        });

        assertThat(count("SELECT count(*) FROM audit_log")).isZero();
    }

    // ---- outbox ----

    private OutboxMessage message(int n) {
        return new OutboxMessage(UUID.randomUUID(), "Batch", "agg-" + n, "BatchRegistered", "production.batch",
                "{\"n\":" + n + "}", START.plusMillis(n));
    }

    @Test
    void savedMessagesAreReadOldestFirstWithTheirPayloadAndTimestamp() {
        OutboxMessage m3 = message(3);
        OutboxMessage m1 = message(1);
        OutboxMessage m2 = message(2);
        tx().executeWithoutResult(s -> {
            outbox.save(m3);
            outbox.save(m1);
            outbox.save(m2);
        });

        List<OutboxMessage> pending = tx().execute(s -> outbox.findPending(10));

        assertThat(pending).extracting(OutboxMessage::id).containsExactly(m1.id(), m2.id(), m3.id());
        assertThat(pending.get(0).payload()).contains("\"n\"");
        assertThat(pending.get(0).createdAt()).isEqualTo(m1.createdAt());
        assertThat(pending.get(0).topic()).isEqualTo("production.batch");
        List<OutboxMessage> limited = tx().execute(s -> outbox.findPending(2));
        assertThat(limited).hasSize(2);
    }

    @Test
    void markPublishedRemovesItFromPendingAndMarkFailedKeepsItWithTheAttemptCounted() {
        OutboxMessage m1 = message(1);
        OutboxMessage m2 = message(2);
        tx().executeWithoutResult(s -> {
            outbox.save(m1);
            outbox.save(m2);
        });

        tx().executeWithoutResult(s -> outbox.markPublished(m1.id(), START.plusSeconds(5)));
        tx().executeWithoutResult(s -> outbox.markFailed(m2.id(), "KafkaException: broker caído"));
        tx().executeWithoutResult(s -> outbox.markFailed(m2.id(), "KafkaException: broker caído"));

        List<OutboxMessage> stillPending = tx().execute(s -> outbox.findPending(10));
        assertThat(stillPending).extracting(OutboxMessage::id).containsExactly(m2.id());
        assertThat(jdbc.sql("SELECT status || '/' || published_at IS NOT NULL FROM outbox WHERE id = :id").param("id", m1.id()).query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("SELECT attempts || '/' || last_error FROM outbox WHERE id = :id").param("id", m2.id()).query(String.class).single())
                .isEqualTo("2/KafkaException: broker caído");
        assertThat(jdbc.sql("SELECT status FROM outbox WHERE id = :id").param("id", m2.id()).query(String.class).single()).isEqualTo("PENDING");
    }

    @Test
    void twoRelaysDoNotReadTheSameRowBecauseOfSkipLocked() throws Exception {
        OutboxMessage m1 = message(1);
        OutboxMessage m2 = message(2);
        OutboxMessage m3 = message(3);
        tx().executeWithoutResult(s -> {
            outbox.save(m1);
            outbox.save(m2);
            outbox.save(m3);
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Future<List<OutboxMessage>> firstRelay = pool.submit(() -> tx().execute(s -> {
                List<OutboxMessage> mine = outbox.findPending(1);
                locked.countDown();
                await(release);
                return mine;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            List<OutboxMessage> secondRelay = pool.submit(() -> tx().execute(s -> outbox.findPending(10))).get(10, TimeUnit.SECONDS);

            assertThat(secondRelay).extracting(OutboxMessage::id).containsExactly(m2.id(), m3.id());
            release.countDown();
            assertThat(firstRelay.get(10, TimeUnit.SECONDS)).extracting(OutboxMessage::id).containsExactly(m1.id());
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("tiempo agotado");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
