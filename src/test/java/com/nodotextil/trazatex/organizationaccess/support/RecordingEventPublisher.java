package com.nodotextil.trazatex.organizationaccess.support;

import com.nodotextil.trazatex.organizationaccess.application.event.InvitationCreatedEvent;
import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationEventPublisher;
import java.util.ArrayList;
import java.util.List;

/** Keeps the published events so tests can inspect them. */
public class RecordingEventPublisher implements OrganizationEventPublisher {

	public final List<InvitationCreatedEvent> invitationCreated = new ArrayList<>();

	@Override
	public void publishInvitationCreated(InvitationCreatedEvent event) {
		invitationCreated.add(event);
	}
}
