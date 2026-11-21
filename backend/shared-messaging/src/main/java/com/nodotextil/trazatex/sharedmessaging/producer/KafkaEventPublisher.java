package com.nodotextil.trazatex.sharedmessaging.producer;

import com.nodotextil.trazatex.sharedkernel.outbox.EventPublishException;
import com.nodotextil.trazatex.sharedkernel.outbox.EventPublisherPort;
import org.springframework.kafka.core.KafkaOperations;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class KafkaEventPublisher implements EventPublisherPort {

    public static final Duration DEFAULT_CONFIRM_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaOperations<String, String> kafka;
    private final Duration confirmTimeout;

    public KafkaEventPublisher(KafkaOperations<String, String> kafka) {
        this(kafka, DEFAULT_CONFIRM_TIMEOUT);
    }

    public KafkaEventPublisher(KafkaOperations<String, String> kafka, Duration confirmTimeout) {
        this.kafka = kafka;
        this.confirmTimeout = confirmTimeout;
    }

    @Override
    public void publish(String topic, String key, String payload) {
        try {
            kafka.send(topic, key, payload).get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishException("Publicación interrumpida en " + topic + " (key " + key + ")", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new EventPublishException("Kafka no confirmó la publicación en " + topic + " (key " + key + ")", e);
        } catch (RuntimeException e) {
            throw new EventPublishException("Kafka no pudo enviar a " + topic + " (key " + key + ")", e);
        }
    }
}
