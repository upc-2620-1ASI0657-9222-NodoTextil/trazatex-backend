package com.nodotextil.trazatex.sharedmessaging.producer;

import com.nodotextil.trazatex.sharedkernel.outbox.EventPublishException;
import com.nodotextil.trazatex.sharedkernel.outbox.EventPublisherPort;
import org.springframework.kafka.core.KafkaOperations;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Adaptador Kafka de {@link EventPublisherPort}. Bloquea hasta que el broker confirma el envío (la plantilla debe
 * venir de {@link ReliableKafkaProducer}, es decir {@code acks=all} + idempotente); si no hay confirmación a tiempo
 * o hay error, lanza {@link EventPublishException} y el relay no marca la fila.
 */
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
            // el envío falla sin llegar a devolver un futuro (p. ej. el tópico no existe y el productor agota la espera de metadatos:
            // KafkaException "Send failed"); el contrato del puerto es EventPublishException en todos los fallos
            throw new EventPublishException("Kafka no pudo enviar a " + topic + " (key " + key + ")", e);
        }
    }
}
