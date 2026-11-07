package com.nodotextil.trazatex.traceability.infrastructure.messaging;

import org.neo4j.driver.exceptions.Neo4jException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Los cinco consumidores corren en hilos distintos y tocan los mismos nodos (empresas, lotes): Neo4j puede abortar una de
 * las transacciones por interbloqueo ({@code Neo.TransientError...}). Es un fallo que se arregla repitiendo, así que se repite
 * aquí —la transacción entera, incluido el registro del {@code ProcessedEvent}— unas pocas veces con una espera corta, antes
 * de dejárselo al manejo de errores de Kafka (que espera segundos y acaba en la DLT).
 */
@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class TransientRetry {

    private static final Logger log = LoggerFactory.getLogger(TransientRetry.class);

    private final int attempts;
    private final Duration backoff;

    public TransientRetry(@Value("${trazatex.messaging.consumer.neo4j-retry.attempts:5}") int attempts,
                   @Value("${trazatex.messaging.consumer.neo4j-retry.backoff:PT0.05S}") Duration backoff) {
        if (attempts < 1 || backoff.isNegative()) {
            throw new IllegalArgumentException("Configuración de reintentos inválida");
        }
        this.attempts = attempts;
        this.backoff = backoff;
    }

    public void run(Runnable action) {
        for (int attempt = 1; ; attempt++) {
            try {
                action.run();
                return;
            } catch (RuntimeException e) {
                if (attempt >= attempts || !isTransient(e)) {
                    throw e;
                }
                log.warn("Fallo transitorio de Neo4j (intento {} de {}), se repite el evento: {}", attempt, attempts, rootMessage(e));
                pause(attempt);
            }
        }
    }

    public static boolean isTransient(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof TransientDataAccessException || cause instanceof ConcurrencyFailureException) {
                return true;
            }
            if (cause instanceof Neo4jException neo4j && neo4j.code() != null && neo4j.code().startsWith("Neo.TransientError")) {
                return true;
            }
        }
        return false;
    }

    private void pause(int attempt) {
        long millis = backoff.toMillis() * attempt;
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis + ThreadLocalRandom.current().nextLong(millis + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido mientras se repetía un evento", e);
        }
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
