package com.nodotextil.trazatex.analytics.infrastructure.messaging;

import com.nodotextil.trazatex.sharedmessaging.consumer.TopicWatcher;
import java.util.List;

/**
 * Tópicos que consume Analytics (docs/architecture/communication.md). Son de Production y Quality: Analytics declara solo sus {@code .DLT} y
 * <b>nunca</b> los crea. Cada tópico tiene su propio grupo y su propio contenedor, que no arranca hasta que el tópico existe (ver
 * {@link TopicWatcherJob}): un mensaje en reintento en un tópico no frena a los demás. Los grupos son los que se reinician para reconstruir
 * Analytics (docs/operations/analytics-rebuild.md).
 */
public final class ConsumedTopics {
    public static final String PRODUCTION_BATCH = "production.batch";
    public static final String PRODUCTION_TRANSFORMATION = "production.transformation";
    public static final String PRODUCTION_TRANSFER = "production.transfer";
    public static final String QUALITY_CONTROL = "quality.control";
    public static final String QUALITY_FAILURE = "quality.failure";

    public static final String BATCH_LISTENER_ID = "analytics-production-batch";
    public static final String TRANSFORMATION_LISTENER_ID = "analytics-production-transformation";
    public static final String TRANSFER_LISTENER_ID = "analytics-production-transfer";
    public static final String CONTROL_LISTENER_ID = "analytics-quality-control";
    public static final String FAILURE_LISTENER_ID = "analytics-quality-failure";

    public static final List<TopicWatcher.Source> ALL = List.of(
            new TopicWatcher.Source(BATCH_LISTENER_ID, PRODUCTION_BATCH),
            new TopicWatcher.Source(TRANSFORMATION_LISTENER_ID, PRODUCTION_TRANSFORMATION),
            new TopicWatcher.Source(TRANSFER_LISTENER_ID, PRODUCTION_TRANSFER),
            new TopicWatcher.Source(CONTROL_LISTENER_ID, QUALITY_CONTROL),
            new TopicWatcher.Source(FAILURE_LISTENER_ID, QUALITY_FAILURE));

    private ConsumedTopics() { }
}
