package com.nodotextil.trazatex.production.infrastructure.messaging;

import com.nodotextil.trazatex.sharedmessaging.consumer.TopicWatcher;

import java.util.List;

public final class ConsumedTopics {

    public static final String ORGACCESS_COMPANY = "orgaccess.company";
    public static final String QUALITY_CONTROL = "quality.control";

    public static final String QUALITY_LISTENER_ID = "production-quality-control";

    public static final List<TopicWatcher.Source> ALL = List.of(new TopicWatcher.Source(QUALITY_LISTENER_ID, QUALITY_CONTROL));

    private ConsumedTopics() {
    }
}
