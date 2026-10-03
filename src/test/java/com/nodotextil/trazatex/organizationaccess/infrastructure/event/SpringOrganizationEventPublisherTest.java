package com.nodotextil.trazatex.organizationaccess.infrastructure.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nodotextil.trazatex.organizationaccess.application.event.InvitationCreatedEvent;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class SpringOrganizationEventPublisherTest {

	@Test
	void publishesTheEventThroughSpring() {
		ApplicationEventPublisher spring = mock(ApplicationEventPublisher.class);
		InvitationCreatedEvent event = new InvitationCreatedEvent(UUID.randomUUID(),
				"a@example.com", UUID.randomUUID(), "OPERATOR", LocalDateTime.now(),
				UUID.randomUUID(), "token");

		new SpringOrganizationEventPublisher(spring).publishInvitationCreated(event);

		verify(spring).publishEvent(event);
	}
}
