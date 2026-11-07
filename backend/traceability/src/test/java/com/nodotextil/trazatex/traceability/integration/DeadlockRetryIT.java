package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.traceability.infrastructure.messaging.TransientRetry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un interbloqueo real de Neo4j (dos transacciones que toman los mismos dos nodos en orden cruzado): una de las dos la aborta
 * el servidor con un error transitorio, {@link TransientRetry} lo reconoce a través de la traducción de Spring y la repite.
 */
class DeadlockRetryIT extends IntegrationTestBase {

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void twoTransactionsLockingTheSameNodesInOppositeOrderBothEndUpApplied() throws Exception {
        neo4j.query("CREATE (:Company {companyId: 'x'}), (:Company {companyId: 'y'})").run();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        TransientRetry retry = new TransientRetry(5, Duration.ofMillis(20));
        CountDownLatch bothHoldTheirFirstLock = new CountDownLatch(2);
        AtomicInteger attempts = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> retry.run(() -> tx.executeWithoutResult(s -> cross("x", "y", bothHoldTheirFirstLock, attempts))));
            Future<?> second = pool.submit(() -> retry.run(() -> tx.executeWithoutResult(s -> cross("y", "x", bothHoldTheirFirstLock, attempts))));
            first.get(60, TimeUnit.SECONDS);
            second.get(60, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(attempts.get()).as("alguna de las dos tuvo que repetirse").isGreaterThan(2);
        assertThat(neo4j.query("MATCH (c:Company) WHERE c.companyId IN ['x', 'y'] RETURN sum(c.touched) AS n").fetch().one().orElseThrow())
                .as("cada empresa recibió las dos escrituras").containsEntry("n", 4L);
    }

    /** Toma el primer nodo, espera a que la otra transacción tome el suyo y pide el segundo: nunca avanzan las dos. */
    private void cross(String firstNode, String secondNode, CountDownLatch bothHoldTheirFirstLock, AtomicInteger attempts) {
        int attempt = attempts.incrementAndGet();
        neo4j.query("MATCH (c:Company {companyId: $id}) SET c.touched = coalesce(c.touched, 0) + 1").bind(firstNode).to("id").run();
        if (attempt <= 2) { // solo en el primer intento de cada una: el reintento ya no se cruza
            bothHoldTheirFirstLock.countDown();
            try {
                bothHoldTheirFirstLock.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        neo4j.query("MATCH (c:Company {companyId: $id}) SET c.touched = coalesce(c.touched, 0) + 1").bind(secondNode).to("id").run();
    }
}
