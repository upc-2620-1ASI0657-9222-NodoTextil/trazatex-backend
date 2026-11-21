package com.nodotextil.trazatex.quality.infrastructure.messaging;

import com.nodotextil.trazatex.sharedmessaging.consumer.TopicWatcher;

import java.util.List;

public final class ConsumedTopics {

    public static final String PRODUCTION_BATCH = "production.batch";
    public static final String PRODUCTION_TRANSFORMATION = "production.transformation";
    public static final String PRODUCTION_TRANSFER = "production.transfer";
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
