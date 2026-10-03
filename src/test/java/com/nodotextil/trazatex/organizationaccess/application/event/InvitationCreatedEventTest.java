package com.nodotextil.trazatex.organizationaccess.application.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class InvitationCreatedEventTest {

	private final UUID invitationId = UUID.randomUUID();
	private final UUID companyId = UUID.randomUUID();
	private final UUID createdBy = UUID.randomUUID();
	private final LocalDateTime expiresAt = LocalDateTime.of(2026, 10, 10, 12, 0);

	private InvitationCreatedEvent newEvent() {
		return new InvitationCreatedEvent(invitationId, "operator@example.com", companyId,
				"OPERATOR", expiresAt, createdBy, "token-123");
	}

	@Test
	void keepsItsData() {
		InvitationCreatedEvent event = newEvent();

		assertThat(event.invitationId()).isEqualTo(invitationId);
		assertThat(event.email()).isEqualTo("operator@example.com");
		assertThat(event.companyId()).isEqualTo(companyId);
		assertThat(event.role()).isEqualTo("OPERATOR");
		assertThat(event.expiresAt()).isEqualTo(expiresAt);
		assertThat(event.createdByUserId()).isEqualTo(createdBy);
		assertThat(event.token()).isEqualTo("token-123");
	}

	@Test
	void exposesStandardEventMetadata() {
		Instant before = Instant.now();
		InvitationCreatedEvent event = newEvent();

		assertThat(event.eventId()).isNotNull();
		assertThat(event.aggregateId()).isEqualTo(invitationId);
		assertThat(event.version()).isEqualTo(1);
		assertThat(event.occurredAt()).isBetween(before, Instant.now());
		assertThat(event.type()).isEqualTo("organizationaccess.invitation.created");
	}

	@Test
	void eachEventHasItsOwnId() {
		assertThat(newEvent().eventId()).isNotEqualTo(newEvent().eventId());
	}

	@Test
	void toStringDoesNotLeakToken() {
		assertThat(newEvent().toString()).doesNotContain("token-123");
	}
}
