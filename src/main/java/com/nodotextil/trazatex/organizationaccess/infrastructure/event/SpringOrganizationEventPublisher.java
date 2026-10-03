package com.nodotextil.trazatex.organizationaccess.infrastructure.event;

import com.nodotextil.trazatex.organizationaccess.application.event.InvitationCreatedEvent;
import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publishes through Spring; consumers listen with {@code @TransactionalEventListener}. */
@Component
class SpringOrganizationEventPublisher implements OrganizationEventPublisher {

	private final ApplicationEventPublisher publisher;

	SpringOrganizationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Override
	public void publishInvitationCreated(InvitationCreatedEvent event) {
		publisher.publishEvent(event);
	}
}
