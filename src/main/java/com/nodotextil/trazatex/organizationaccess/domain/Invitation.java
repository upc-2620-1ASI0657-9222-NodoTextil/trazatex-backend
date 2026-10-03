package com.nodotextil.trazatex.organizationaccess.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An invitation to join a company as {@link Role#COMPANY_ADMIN} or {@link Role#OPERATOR}.
 * The person's names and job title are optional here; the use cases decide when they are
 * mandatory.
 */
public final class Invitation {

	/** A pending invitation expires this long after it was created (RF-005). */
	public static final Duration VALIDITY = Duration.ofDays(7);

	private final UUID id;
	private final String email;
	private final UUID companyId;
	private final Role role;
	private final String token;
	private final String firstName;
	private final String lastName;
	private final String jobTitle;
	private final LocalDateTime createdAt;
	private final LocalDateTime expiresAt;
	private final UUID createdByUserId;
	private final InvitationStatus status;

	public Invitation(UUID id, String email, UUID companyId, Role role, String token,
			String firstName, String lastName, String jobTitle, LocalDateTime createdAt,
			LocalDateTime expiresAt, UUID createdByUserId, InvitationStatus status) {
		this.role = DomainText.notNull(role, "role");
		if (!role.isInvitable()) {
			throw new OrganizationValidationException(
					"An invitation can only be for a COMPANY_ADMIN or an OPERATOR");
		}
		this.id = DomainText.notNull(id, "id");
		this.email = DomainText.email(email);
		this.companyId = DomainText.notNull(companyId, "companyId");
		this.token = DomainText.required(token, "token");
		this.firstName = DomainText.optional(firstName);
		this.lastName = DomainText.optional(lastName);
		this.jobTitle = DomainText.optional(jobTitle);
		this.createdAt = DomainText.notNull(createdAt, "createdAt");
		this.expiresAt = DomainText.notNull(expiresAt, "expiresAt");
		this.createdByUserId = DomainText.notNull(createdByUserId, "createdByUserId");
		this.status = DomainText.notNull(status, "status");
	}

	/** A new pending invitation that expires {@link #VALIDITY} after {@code now}. */
	public static Invitation issue(String email, UUID companyId, Role role, String token,
			String firstName, String lastName, String jobTitle, UUID createdByUserId,
			LocalDateTime now) {
		return new Invitation(UUID.randomUUID(), email, companyId, role, token, firstName,
				lastName, jobTitle, now, now.plus(VALIDITY), createdByUserId,
				InvitationStatus.PENDING);
	}

	public UUID id() {
		return id;
	}

	public String email() {
		return email;
	}

	public UUID companyId() {
		return companyId;
	}

	public Role role() {
		return role;
	}

	public String token() {
		return token;
	}

	public String firstName() {
		return firstName;
	}

	public String lastName() {
		return lastName;
	}

	public String jobTitle() {
		return jobTitle;
	}

	public LocalDateTime createdAt() {
		return createdAt;
	}

	public LocalDateTime expiresAt() {
		return expiresAt;
	}

	public UUID createdByUserId() {
		return createdByUserId;
	}

	public InvitationStatus status() {
		return status;
	}
}
