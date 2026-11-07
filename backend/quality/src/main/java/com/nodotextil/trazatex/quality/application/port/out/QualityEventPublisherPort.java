package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.event.QualityEvent;

/**
 * Registra el evento en el outbox dentro de la transacción del caso de uso (ADR-004). No publica en Kafka: eso lo hace el relay después
 * del commit, así que una operación que se revierte no deja ningún evento.
 */
public interface QualityEventPublisherPort {

    void publish(QualityEvent event);
}
