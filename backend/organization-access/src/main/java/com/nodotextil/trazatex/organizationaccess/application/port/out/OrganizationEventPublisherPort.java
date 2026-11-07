package com.nodotextil.trazatex.organizationaccess.application.port.out;

import com.nodotextil.trazatex.organizationaccess.domain.event.OrganizationEvent;

/**
 * Registra el evento en el outbox dentro de la transacción del caso de uso (ADR-004). No publica en Kafka: eso lo hace
 * el relay después del commit.
 */
public interface OrganizationEventPublisherPort {

    void publish(OrganizationEvent event);
}
