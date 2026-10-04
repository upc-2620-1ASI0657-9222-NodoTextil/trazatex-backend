package com.nodotextil.trazatex.organizationaccess.application.port;

import com.nodotextil.trazatex.organizationaccess.application.event.InvitationCreatedEvent;

/** Publishes the module's events to the rest of the monolith. */
public interface OrganizationEventPublisher {

	void publishInvitationCreated(InvitationCreatedEvent event);
}
