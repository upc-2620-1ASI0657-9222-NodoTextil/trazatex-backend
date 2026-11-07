package com.nodotextil.trazatex.sharedkernel.outbox;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Doble de prueba con lo que exige {@link OutboxStorePort} a una base real: las escrituras se deshacen si la
 * transacción se revierte, y {@code findPending} bloquea las filas devueltas para la transacción (el equivalente de
 * {@code FOR UPDATE SKIP LOCKED}: otra transacción las salta). Devuelve las filas en orden de inserción, no de
 * {@code createdAt}, para comprobar que el relay ordena por sí mismo.
 */
class InMemoryOutboxStore implements OutboxStorePort {

    static final class Row {
        final OutboxMessage message;
        volatile boolean published;
        volatile Instant publishedAt;
        volatile int attempts;
        volatile String lastError;

        Row(OutboxMessage message) {
            this.message = message;
        }
    }

    private final List<Row> rows = new CopyOnWriteArrayList<>();
    private final Map<UUID, Thread> locks = new ConcurrentHashMap<>();
    private volatile RuntimeException markFailure;
    private volatile RuntimeException markFailedFailure;

    @Override
    public void save(OutboxMessage message) {
        requireTransaction();
        Row row = new Row(message);
        rows.add(row);
        afterRollback(() -> rows.remove(row));
    }

    @Override
    public List<OutboxMessage> findPending(int limit) {
        requireTransaction();
        Thread me = Thread.currentThread();
        List<OutboxMessage> found = rows.stream()
                .filter(row -> !row.published)
                .filter(row -> locks.get(row.message.id()) == null || locks.get(row.message.id()) == me)
                .limit(limit)
                .map(row -> row.message)
                .toList();
        found.forEach(message -> locks.put(message.id(), me));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                found.forEach(message -> locks.remove(message.id(), me));
            }
        });
        return found;
    }

    @Override
    public void markPublished(UUID id, Instant publishedAt) {
        requireTransaction();
        if (markFailure != null) {
            throw markFailure;
        }
        Row row = rows.stream().filter(r -> r.message.id().equals(id)).findFirst().orElseThrow();
        row.published = true;
        row.publishedAt = publishedAt;
        afterRollback(() -> {
            row.published = false;
            row.publishedAt = null;
        });
    }

    @Override
    public void markFailed(UUID id, String error) {
        requireTransaction();
        if (markFailedFailure != null) {
            throw markFailedFailure;
        }
        Row row = rows.stream().filter(r -> r.message.id().equals(id)).findFirst().orElseThrow();
        int previousAttempts = row.attempts;
        String previousError = row.lastError;
        row.attempts = previousAttempts + 1;
        row.lastError = error;
        afterRollback(() -> {
            row.attempts = previousAttempts;
            row.lastError = previousError;
        });
    }

    void failMarkPublishedWith(RuntimeException failure) {
        markFailure = failure;
    }

    void failMarkFailedWith(RuntimeException failure) {
        markFailedFailure = failure;
    }

    int attempts(UUID id) {
        return rows.stream().filter(r -> r.message.id().equals(id)).findFirst().orElseThrow().attempts;
    }

    String lastError(UUID id) {
        return rows.stream().filter(r -> r.message.id().equals(id)).findFirst().orElseThrow().lastError;
    }

    boolean isPublished(UUID id) {
        return rows.stream().filter(r -> r.message.id().equals(id)).anyMatch(r -> r.published);
    }

    Instant publishedAt(UUID id) {
        return rows.stream().filter(r -> r.message.id().equals(id)).findFirst().orElseThrow().publishedAt;
    }

    int size() {
        return rows.size();
    }

    long pendingCount() {
        return rows.stream().filter(row -> !row.published).count();
    }

    private static void afterRollback(Runnable undo) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    undo.run();
                }
            }
        });
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("El outbox debe usarse dentro de una transacción");
        }
    }
}
