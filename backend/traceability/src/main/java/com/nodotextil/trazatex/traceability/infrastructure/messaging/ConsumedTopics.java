package com.nodotextil.trazatex.traceability.infrastructure.messaging;

import com.nodotextil.trazatex.sharedmessaging.consumer.TopicWatcher;

import java.util.List;

/**
 * Tópicos que consume traceability (docs/architecture/communication.md). Son de otros servicios: traceability declara solo sus
 * {@code .DLT} y <b>nunca</b> los crea. Cada uno se lee con su propio grupo y su propio contenedor, que no arranca hasta que el
 * tópico existe (ver {@link TopicWatcherJob}).
 */
public final class ConsumedTopics {

    public static final String ORGACCESS_COMPANY = "orgaccess.company";
    public static final String PRODUCTION_BATCH = "production.batch";
    public static final String PRODUCTION_TRANSFORMATION = "production.transformation";
    public static final String PRODUCTION_TRANSFER = "production.transfer";
    public static final String QUALITY_CONTROL = "quality.control";
    public static final String QUALITY_FAILURE = "quality.failure";

    public static final String COMPANY_LISTENER_ID = "traceability-orgaccess-company";
    public static final String BATCH_LISTENER_ID = "traceability-production-batch";
    public static final String TRANSFORMATION_LISTENER_ID = "traceability-production-transformation";
    public static final String TRANSFER_LISTENER_ID = "traceability-production-transfer";
    public static final String QUALITY_LISTENER_ID = "traceability-quality-control";
    public static final String FAILURE_LISTENER_ID = "traceability-quality-failure";

    public static final List<TopicWatcher.Source> ALL = List.of(
            new TopicWatcher.Source(COMPANY_LISTENER_ID, ORGACCESS_COMPANY),
            new TopicWatcher.Source(BATCH_LISTENER_ID, PRODUCTION_BATCH),
            new TopicWatcher.Source(TRANSFORMATION_LISTENER_ID, PRODUCTION_TRANSFORMATION),
            new TopicWatcher.Source(TRANSFER_LISTENER_ID, PRODUCTION_TRANSFER),
            new TopicWatcher.Source(QUALITY_LISTENER_ID, QUALITY_CONTROL),
            new TopicWatcher.Source(FAILURE_LISTENER_ID, QUALITY_FAILURE),
            new TopicWatcher.Source("traceability-quality-tests", QUALITY_CONTROL),
            new TopicWatcher.Source("traceability-failure-details", QUALITY_FAILURE));

    private ConsumedTopics() {
    }
}
