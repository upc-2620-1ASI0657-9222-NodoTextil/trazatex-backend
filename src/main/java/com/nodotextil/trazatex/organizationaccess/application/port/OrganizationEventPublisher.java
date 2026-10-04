package com.nodotextil.trazatex.organizationaccess.application.port;

import com.nodotextil.trazatex.organizationaccess.application.event.InvitationCreatedEvent;


public interface OrganizationEventPublisher {

	void publishInvitationCreated(InvitationCreatedEvent event);
}
