package com.nodotextil.trazatex.organizationaccess.application.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;


public final class InvitationCreatedEvent {

	public static final String TYPE = "organizationaccess.invitation.created";
	private static final int VERSION = 1;

	private final UUID eventId;
	private final Instant occurredAt;
	private final UUID invitationId;
	private final String email;
	private final UUID companyId;
	private final String role;
	private final LocalDateTime expiresAt;
	private final UUID createdByUserId;
	private final String token;

	public InvitationCreatedEvent(UUID invitationId, String email, UUID companyId, String role,
			LocalDateTime expiresAt, UUID createdByUserId, String token) {
		this.eventId = UUID.randomUUID();
		this.occurredAt = Instant.now();
		this.invitationId = Objects.requireNonNull(invitationId, "invitationId");
		this.email = email;
		this.companyId = companyId;
		this.role = role;
		this.expiresAt = expiresAt;
		this.createdByUserId = createdByUserId;
		this.token = token;
	}

	public UUID eventId() {
		return eventId;
	}

	public UUID aggregateId() {
		return invitationId;
	}

	public int version() {
		return VERSION;
	}

	public Instant occurredAt() {
		return occurredAt;
	}

	public String type() {
		return TYPE;
	}

	public UUID invitationId() {
		return invitationId;
	}

	public String email() {
		return email;
	}

	public UUID companyId() {
		return companyId;
	}

	public String role() {
		return role;
	}

	public LocalDateTime expiresAt() {
		return expiresAt;
	}

	public UUID createdByUserId() {
		return createdByUserId;
	}

	public String token() {
		return token;
	}

	@Override
	public String toString() {
		return "InvitationCreatedEvent[eventId=" + eventId + ", invitationId=" + invitationId
				+ ", companyId=" + companyId + ", role=" + role + "]";
	}
}
