package com.nodotextil.trazatex.analytics.infrastructure.messaging;

import com.nodotextil.trazatex.sharedmessaging.consumer.TopicWatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class TopicWatcherJob {
    private final TopicWatcher watcher;
    TopicWatcherJob(TopicWatcher watcher) { this.watcher=watcher; }

    @Scheduled(initialDelay = 500, fixedDelayString = "${trazatex.messaging.consumer.topic-check-interval:PT15S}")
    void startConsumersWhoseTopicExists() { watcher.startConsumersWhoseTopicExists(); }
}
