package com.nodotextil.trazatex.sharedmessaging.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Reintentos con espera creciente y, agotados, envío a {@code <topic>.DLT} conservando la clave (ADR-004).
 * Registrar el resultado como {@code CommonErrorHandler} de la fábrica de contenedores del servicio.
 */
public final class KafkaConsumerErrorHandling {

    public static final String DLT_SUFFIX = ".DLT";

    /** @param maxRetries reintentos tras el primer intento fallido (el mensaje se intenta {@code maxRetries + 1} veces) */
    public record RetrySettings(int maxRetries, Duration initialInterval, double multiplier, Duration maxInterval) {

        public static final RetrySettings DEFAULT = new RetrySettings(5, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(30));

        public RetrySettings {
            if (maxRetries < 0 || multiplier < 1.0 || initialInterval.isNegative() || maxInterval.compareTo(initialInterval) < 0) {
                throw new IllegalArgumentException("Configuración de reintentos inválida");
            }
        }
    }

    private KafkaConsumerErrorHandling() {
    }

    public static DefaultErrorHandler deadLetterErrorHandler(KafkaOperations<?, ?> dltTemplate) {
        return deadLetterErrorHandler(dltTemplate, RetrySettings.DEFAULT);
    }

    public static DefaultErrorHandler deadLetterErrorHandler(KafkaOperations<?, ?> dltTemplate, RetrySettings settings) {
        return deadLetterErrorHandler(dltTemplate, settings, exception -> null, (record, exception) -> {
        });
    }

    /**
     * Igual que {@link #deadLetterErrorHandler(KafkaOperations, RetrySettings)} pero con reintentos propios para ciertos errores y un aviso al agotarlos.
     *
     * @param settingsFor los reintentos de un error concreto (por ejemplo, uno que se resuelve cuando llegan eventos de otro servicio y necesita más espera);
     *                    {@code null} para usar {@code settings}. Recibe la excepción tal como la lanzó el contenedor: hay que mirar su cadena de causas
     * @param onDeadLetter se invoca <b>después</b> de que el mensaje quedó en la DLT (no si no se pudo publicar en ella), para dejar un aviso
     */
    public static DefaultErrorHandler deadLetterErrorHandler(KafkaOperations<?, ?> dltTemplate, RetrySettings settings, Function<Exception, RetrySettings> settingsFor,
                                                             BiConsumer<ConsumerRecord<?, ?>, Exception> onDeadLetter) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dltTemplate,
                (record, exception) -> new TopicPartition(deadLetterTopic(record), -1)); // -1: que el productor elija la partición
        ConsumerRecordRecoverer publishAndWarn = (record, exception) -> {
            recoverer.accept(record, exception);
            onDeadLetter.accept(record, exception);
        };
        DefaultErrorHandler handler = new DefaultErrorHandler(publishAndWarn, backOff(settings));
        handler.setBackOffFunction((record, exception) -> {
            RetrySettings specific = settingsFor.apply(exception);
            return specific == null ? null : backOff(specific); // nulo: los reintentos generales
        });
        // Un sobre ilegible o un evento que no se puede aplicar nunca mejora al reintentarlo (incluye MalformedEventException)
        handler.addNotRetryableExceptions(NonRetryableEventException.class);
        return handler;
    }

    public static String deadLetterTopic(ConsumerRecord<?, ?> record) {
        return record.topic() + DLT_SUFFIX;
    }

    static BackOff backOff(RetrySettings settings) {
        if (settings.maxRetries() == 0) {
            // ExponentialBackOffWithMaxRetries(0) igual concede un reintento; aquí el primer fallo va directo a la DLT
            return new FixedBackOff(0L, 0L);
        }
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(settings.maxRetries());
        backOff.setInitialInterval(settings.initialInterval().toMillis());
        backOff.setMultiplier(settings.multiplier());
        backOff.setMaxInterval(settings.maxInterval().toMillis());
        return backOff;
    }
}
