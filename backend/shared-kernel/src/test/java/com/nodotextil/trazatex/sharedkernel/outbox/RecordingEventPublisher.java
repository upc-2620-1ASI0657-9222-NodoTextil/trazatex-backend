package com.nodotextil.trazatex.sharedkernel.outbox;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/** Publicador de prueba: anota lo publicado, puede fallar para ciertas claves/tópicos y observar el momento de publicar. */
class RecordingEventPublisher implements EventPublisherPort {

    record Published(String topic, String key, String payload) {
    }

    private final List<Published> published = new CopyOnWriteArrayList<>();
    private final Set<String> failingStreams = new CopyOnWriteArraySet<>();
    private volatile Consumer<Published> beforeConfirm = p -> { };

    @Override
    public void publish(String topic, String key, String payload) {
        Published message = new Published(topic, key, payload);
        beforeConfirm.accept(message);
        if (failingStreams.contains(topic + "|" + key)) {
            throw new EventPublishException("sin confirmación de Kafka para " + key, new RuntimeException("broker caído"));
        }
        published.add(message);
    }

    void failFor(String topic, String key) {
        failingStreams.add(topic + "|" + key);
    }

    void recover() {
        failingStreams.clear();
    }

    /** Se ejecuta justo antes de que el "broker" confirme cada mensaje. */
    void beforeConfirm(Consumer<Published> hook) {
        this.beforeConfirm = hook;
    }

    List<Published> published() {
        return published;
    }

    List<String> payloads() {
        return published.stream().map(Published::payload).toList();
    }

    static OutboxMessage message(UUID id, String topic, String aggregateId, String payload, java.time.Instant createdAt) {
        return new OutboxMessage(id, "Batch", aggregateId, "BatchRegistered", topic, payload, createdAt);
    }
}
