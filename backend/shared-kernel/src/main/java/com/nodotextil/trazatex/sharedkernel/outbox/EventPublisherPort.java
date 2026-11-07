package com.nodotextil.trazatex.sharedkernel.outbox;

/** Salida hacia el broker. La implementación para Kafka vive en {@code shared-messaging}. */
public interface EventPublisherPort {

    /**
     * Publica el mensaje y <b>espera la confirmación del broker</b> (con {@code acks=all} en Kafka): si retorna, el
     * mensaje está durable; si no se confirma, lanza {@link EventPublishException}. El relay solo marca como
     * publicada una fila después de que este método retorna.
     *
     * @param key clave del mensaje: el {@code aggregateId}, para conservar el orden por agregado
     */
    void publish(String topic, String key, String payload);
}
