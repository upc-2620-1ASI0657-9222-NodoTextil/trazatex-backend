package com.nodotextil.trazatex.traceability.infrastructure.messaging;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.exceptions.TransientException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DuplicateKeyException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Un interbloqueo de Neo4j entre consumidores se repite; cualquier otro fallo no. */
class TransientRetryTest {

    private static final TransientException DEADLOCK = new TransientException("Neo.TransientError.Transaction.DeadlockDetected", "interbloqueo");

    private final TransientRetry retry = new TransientRetry(3, Duration.ZERO);

    @Test
    void aTransientFailureIsRepeatedUntilItSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        retry.run(() -> {
            if (calls.incrementAndGet() < 3) {
                throw DEADLOCK;
            }
        });

        assertThat(calls).hasValue(3);
    }

    @Test
    void aTransientFailureThatNeverGoesAwayIsRethrownAfterTheLastAttempt() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retry.run(() -> {
            calls.incrementAndGet();
            throw DEADLOCK;
        })).isSameAs(DEADLOCK);

        assertThat(calls).hasValue(3);
    }

    @Test
    void aFailureThatRepeatingCannotFixIsNotRepeated() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retry.run(() -> {
            calls.incrementAndGet();
            throw new ClientException("Neo.ClientError.Schema.ConstraintValidationFailed", "ya existe");
        })).isInstanceOf(ClientException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void theClassificationLooksThroughTheCauseChainAndSpringsTranslation() {
        assertThat(TransientRetry.isTransient(new IllegalStateException("envuelto", DEADLOCK))).isTrue();
        assertThat(TransientRetry.isTransient(new DeadlockLoserDataAccessException("traducido por Spring", DEADLOCK))).isTrue();
        assertThat(TransientRetry.isTransient(new ClientException("Neo.ClientError.Statement.SyntaxError", "mal"))).isFalse();
        assertThat(TransientRetry.isTransient(new DuplicateKeyException("duplicado"))).isFalse();
        assertThat(TransientRetry.isTransient(new IllegalArgumentException("otra cosa"))).isFalse();
    }

    @Test
    void theNumberOfAttemptsMustBeAtLeastOne() {
        assertThatThrownBy(() -> new TransientRetry(0, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
