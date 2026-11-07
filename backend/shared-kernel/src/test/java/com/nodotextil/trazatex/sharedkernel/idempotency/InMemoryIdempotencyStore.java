package com.nodotextil.trazatex.sharedkernel.idempotency;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Doble de prueba que imita lo que exige {@link IdempotencyStorePort} a una base real: la reserva es parte de la
 * transacción (si se revierte, la clave desaparece) y un {@code tryReserve} concurrente sobre la misma clave espera
 * a que la transacción dueña confirme o revierta, como haría la PK de PostgreSQL.
 */
class InMemoryIdempotencyStore implements IdempotencyStorePort {

    private static final class Entry {
        volatile IdempotencyRecord record;
        final CountDownLatch finished = new CountDownLatch(1);

        Entry(IdempotencyRecord record) {
            this.record = record;
        }
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public boolean tryReserve(String key, String userId, String requestHash, Instant createdAt) {
        requireTransaction();
        while (true) {
            Entry mine = new Entry(new IdempotencyRecord(key, userId, requestHash, 0, null, createdAt));
            Entry existing = entries.putIfAbsent(key, mine);
            if (existing == null) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            entries.remove(key, mine);
                        }
                        mine.finished.countDown();
                    }
                });
                return true;
            }
            awaitOwner(existing);
            if (entries.get(key) == existing) {
                return false;
            }
        }
    }

    @Override
    public Optional<IdempotencyRecord> find(String key) {
        requireTransaction();
        return Optional.ofNullable(entries.get(key)).map(entry -> entry.record);
    }

    @Override
    public void complete(String key, int responseStatus, String responseBody) {
        requireTransaction();
        Entry entry = entries.get(key);
        IdempotencyRecord current = entry.record;
        entry.record = new IdempotencyRecord(key, current.userId(), current.requestHash(), responseStatus,
                responseBody, current.createdAt());
    }

    @Override
    public void delete(String key) {
        requireTransaction();
        entries.remove(key);
    }

    @Override
    public int deleteCreatedBefore(Instant cutoff) {
        int before = entries.size();
        entries.values().removeIf(entry -> entry.record.createdAt().isBefore(cutoff));
        return before - entries.size();
    }

    Optional<IdempotencyRecord> stored(String key) {
        return Optional.ofNullable(entries.get(key)).map(entry -> entry.record);
    }

    int size() {
        return entries.size();
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("El almacén de idempotencia debe usarse dentro de una transacción");
        }
    }

    private static void awaitOwner(Entry owner) {
        try {
            if (!owner.finished.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Tiempo agotado esperando a la transacción dueña de la clave");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
