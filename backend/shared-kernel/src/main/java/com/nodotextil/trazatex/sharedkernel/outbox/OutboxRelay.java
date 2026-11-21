package com.nodotextil.trazatex.sharedkernel.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OutboxRelay {

    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final int MAX_ERROR_LENGTH = 1000;

    public record Result(int published, int failed, int skipped) {
    }

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxStorePort store;
    private final EventPublisherPort publisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final int batchSize;

    public OutboxRelay(OutboxStorePort store, EventPublisherPort publisher, PlatformTransactionManager transactionManager) {
        this(store, publisher, transactionManager, Clock.systemUTC(), DEFAULT_BATCH_SIZE);
    }

    public OutboxRelay(OutboxStorePort store, EventPublisherPort publisher, PlatformTransactionManager transactionManager,
                       Clock clock, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize debe ser >= 1");
        }
        this.store = store;
        this.publisher = publisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.batchSize = batchSize;
    }

    public Result publishPending() {
        return transactionTemplate.execute(tx -> publishBatch());
    }

    private Result publishBatch() {
        List<OutboxMessage> pending = store.findPending(batchSize).stream()
                .sorted(Comparator.comparing(OutboxMessage::createdAt))
                .toList();
        Set<String> blocked = new HashSet<>();
        int published = 0;
        int failed = 0;
        int skipped = 0;
        for (OutboxMessage message : pending) {
            String stream = message.topic() + "|" + message.aggregateId();
            if (blocked.contains(stream)) {
                skipped++;
                continue;
            }
            try {
                publisher.publish(message.topic(), message.aggregateId(), message.payload());
            } catch (RuntimeException e) {
                blocked.add(stream);
                failed++;
                recordFailure(message, e);
                continue;
            }
            store.markPublished(message.id(), clock.instant());
            published++;
        }
        return new Result(published, failed, skipped);
    }

    private void recordFailure(OutboxMessage message, RuntimeException failure) {
        String error = describe(failure);
        log.warn("No se pudo publicar el evento {} en el tópico {}: {}", message.id(), message.topic(), error);
        try {
            store.markFailed(message.id(), error);
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar el intento fallido del evento {}: {}", message.id(), e.toString());
        }
    }

    static String describe(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String text = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        if (root != failure) {
            text += " (causa: " + root.getClass().getSimpleName() + ": " + root.getMessage() + ")";
        }
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH - 1) + "…";
    }
}
