package com.nodotextil.trazatex.quality.domain.event;

/**
 * Payload de un evento del catálogo (docs/architecture/communication.md). El sobre ({@code eventId}, {@code occurredAt}...) lo arma
 * la capa de infraestructura al registrarlo en el outbox (06c); {@link #eventType()} y {@link #aggregateId()} son el nombre del
 * evento y la clave del mensaje en Kafka. Al ser records, solo sus componentes se serializan.
 */
public interface QualityEvent {

    String eventType();

    String aggregateId();
}
