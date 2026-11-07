package com.nodotextil.trazatex.organizationaccess.domain.event;

/**
 * Payload de un evento del catálogo (docs/architecture/communication.md). El sobre ({@code eventId}, {@code occurredAt}...)
 * lo arma la capa de aplicación al registrarlo en el outbox; {@link #type()} y {@link #aggregateId()} son el nombre del
 * evento y la clave del mensaje en Kafka. Al ser un record, solo sus componentes se serializan.
 */
public interface OrganizationEvent {

    String type();

    String aggregateId();
}
