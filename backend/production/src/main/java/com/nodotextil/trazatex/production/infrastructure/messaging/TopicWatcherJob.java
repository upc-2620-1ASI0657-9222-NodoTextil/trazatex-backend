package com.nodotextil.trazatex.production.infrastructure.messaging;

import com.nodotextil.trazatex.sharedmessaging.consumer.TopicWatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Programa el vigilante de {@code shared-messaging} sobre {@link ConsumedTopics#ALL}: el contenedor de {@code quality.control} (tópico de
 * Quality, que puede no existir todavía) no arranca solo y cada pasada
 * ({@code trazatex.messaging.consumer.quality.topic-check-interval}, 15 s) lo arranca cuando el tópico aparece.
 */
@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class TopicWatcherJob {

    private final TopicWatcher watcher;

    TopicWatcherJob(TopicWatcher watcher) {
        this.watcher = watcher;
    }

    @Scheduled(initialDelay = 1000, fixedDelayString = "${trazatex.messaging.consumer.quality.topic-check-interval:PT15S}")
    void startConsumersWhoseTopicExists() {
        watcher.startConsumersWhoseTopicExists();
    }
}
