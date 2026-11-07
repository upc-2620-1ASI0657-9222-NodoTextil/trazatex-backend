package com.nodotextil.trazatex.quality.infrastructure.messaging;

import com.nodotextil.trazatex.sharedmessaging.consumer.TopicWatcher;

import java.util.List;

/**
 * Tópicos que consume quality (docs/architecture/communication.md). Son de Production y Traceability: quality declara solo sus {@code .DLT} y
 * <b>nunca</b> los crea. Cada uno se lee con su propio grupo y su propio contenedor, que no arranca hasta que el tópico existe
 * (ver {@link TopicWatcherJob}).
 */
public final class ConsumedTopics {

    public static final String PRODUCTION_BATCH = "production.batch";
    public static final String PRODUCTION_TRANSFORMATION = "production.transformation";
    public static final String PRODUCTION_TRANSFER = "production.transfer";
    /** De Traceability: {@code PossibleDerivedFailureDetected}. */
    public static final String TRACEABILITY_IMPACT = "traceability.impact";

    public static final String BATCH_LISTENER_ID = "quality-production-batch";
    public static final String TRANSFORMATION_LISTENER_ID = "quality-production-transformation";
    public static final String TRANSFER_LISTENER_ID = "quality-production-transfer";
    public static final String IMPACT_LISTENER_ID = "quality-traceability-impact";

    public static final List<TopicWatcher.Source> ALL = List.of(
            new TopicWatcher.Source(BATCH_LISTENER_ID, PRODUCTION_BATCH),
            new TopicWatcher.Source(TRANSFORMATION_LISTENER_ID, PRODUCTION_TRANSFORMATION),
            new TopicWatcher.Source(TRANSFER_LISTENER_ID, PRODUCTION_TRANSFER),
            new TopicWatcher.Source(IMPACT_LISTENER_ID, TRACEABILITY_IMPACT));

    private ConsumedTopics() {
    }
}
