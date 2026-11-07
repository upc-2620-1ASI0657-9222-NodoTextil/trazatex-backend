package com.nodotextil.trazatex.notification.infrastructure.messaging;

import com.nodotextil.trazatex.sharedmessaging.consumer.TopicWatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Programa el vigilante de {@code shared-messaging} sobre {@link ConsumedTopics#ALL}: cada pasada
 * ({@code trazatex.messaging.consumer.topic-check-interval}, 15 s) arranca los contenedores de los tópicos ajenos que ya existen.
 */
@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class TopicWatcherJob {
    private final TopicWatcher watcher;
    TopicWatcherJob(TopicWatcher watcher) { this.watcher=watcher; }

    @Scheduled(initialDelay = 500, fixedDelayString = "${trazatex.messaging.consumer.topic-check-interval:PT15S}")
    void startConsumersWhoseTopicExists() { watcher.startConsumersWhoseTopicExists(); }
}
