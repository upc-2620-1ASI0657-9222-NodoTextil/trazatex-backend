package com.nodotextil.trazatex.sharedmessaging.consumer;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Doble de prueba de {@link ProcessedEventPort}: el registro es parte de la transacción (se deshace al revertir) y un
 * {@code markProcessed} concurrente del mismo {@code eventId} espera a la transacción dueña, como la PK de PostgreSQL.
 */
public class InMemoryProcessedEventStore implements ProcessedEventPort {

    private final ConcurrentHashMap<UUID, CountDownLatch> entries = new ConcurrentHashMap<>();

    @Override
    public boolean markProcessed(UUID eventId, String eventType) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("processed_events debe usarse dentro de una transacción");
        }
        while (true) {
            CountDownLatch mine = new CountDownLatch(1);
            CountDownLatch existing = entries.putIfAbsent(eventId, mine);
            if (existing == null) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            entries.remove(eventId, mine);
                        }
                        mine.countDown();
                    }
                });
                return true;
            }
            await(existing);
            if (entries.get(eventId) == existing) {
                return false;
            }
        }
    }

    public int size() {
        return entries.size();
    }

    public boolean contains(UUID eventId) {
        return entries.containsKey(eventId);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Tiempo agotado esperando a la transacción dueña del evento");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
