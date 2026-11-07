package com.nodotextil.trazatex.organizationaccess.infrastructure.messaging;

import com.nodotextil.trazatex.organizationaccess.application.port.out.OrganizationEventPublisherPort;
import com.nodotextil.trazatex.organizationaccess.domain.event.AdminRequestResolved;
import com.nodotextil.trazatex.organizationaccess.domain.event.CompanyPrivacyUpdated;
import com.nodotextil.trazatex.organizationaccess.domain.event.CompanyRegistered;
import com.nodotextil.trazatex.organizationaccess.domain.event.CompanyStatusChanged;
import com.nodotextil.trazatex.organizationaccess.domain.event.InvitationCreated;
import com.nodotextil.trazatex.organizationaccess.domain.event.OrganizationEvent;
import com.nodotextil.trazatex.organizationaccess.domain.event.UserAccountUpdated;
import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxEventRecorder;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Escribe el evento en el outbox, en la transacción del caso de uso (ADR-004). Tópicos del catálogo
 * ({@code docs/architecture/communication.md}); el relay (02e) los publica en Kafka tras el commit.
 */
@Component
class OutboxOrganizationEventPublisher implements OrganizationEventPublisherPort {

    private final OutboxEventRecorder recorder;
    private final Clock clock;

    OutboxOrganizationEventPublisher(OutboxEventRecorder recorder, Clock clock) {
        this.recorder = recorder;
        this.clock = clock;
    }

    @Override
    public void publish(OrganizationEvent event) {
        recorder.record(aggregateType(event), topic(event),
                BaseDomainEvent.of(event.type(), event.aggregateId(), event, clock));
    }

    private static String aggregateType(OrganizationEvent event) {
        return switch (event) {
            case CompanyRegistered e -> "Company";
            case CompanyStatusChanged e -> "Company";
            case CompanyPrivacyUpdated e -> "Company";
            case InvitationCreated e -> "Invitation";
            case AdminRequestResolved e -> "AdminRequest";
            case com.nodotextil.trazatex.organizationaccess.domain.event.AdminRequestSubmitted e -> "AdminRequest";
            case UserAccountUpdated e -> "User";
            default -> throw new IllegalArgumentException("Evento sin ruta: " + event.type());
        };
    }

    private static String topic(OrganizationEvent event) {
        return switch (event) {
            case CompanyRegistered e -> Topics.COMPANY;
            case CompanyStatusChanged e -> Topics.COMPANY;
            case CompanyPrivacyUpdated e -> Topics.COMPANY;
            case InvitationCreated e -> Topics.INVITATION;
            case AdminRequestResolved e -> Topics.INVITATION;
            case com.nodotextil.trazatex.organizationaccess.domain.event.AdminRequestSubmitted e -> Topics.INVITATION;
            case UserAccountUpdated e -> Topics.USER;
            default -> throw new IllegalArgumentException("Evento sin ruta: " + event.type());
        };
    }
}
