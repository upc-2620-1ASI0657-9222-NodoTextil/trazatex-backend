package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.event.InvitationCreatedEvent;
import com.nodotextil.trazatex.organizationaccess.application.port.InvitationRepository;
import com.nodotextil.trazatex.organizationaccess.application.port.LicenseRepository;
import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationEventPublisher;
import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.License;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationConflictException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationNotFoundException;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Creates invitations for every use case that needs one: checks that the email is free and that
 * the company has a seat (RF-013), generates the token, saves the invitation and publishes
 * {@link InvitationCreatedEvent}. Run it inside the caller's transaction.
 */
@Component
class InvitationIssuer {

	private static final int TOKEN_BYTES = 32;

	private final InvitationRepository invitations;
	private final OrganizationUserRepository users;
	private final LicenseRepository licenses;
	private final OrganizationEventPublisher events;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	@Autowired
	InvitationIssuer(InvitationRepository invitations, OrganizationUserRepository users,
			LicenseRepository licenses, OrganizationEventPublisher events) {
		this(invitations, users, licenses, events, Clock.systemUTC());
	}

	InvitationIssuer(InvitationRepository invitations, OrganizationUserRepository users,
			LicenseRepository licenses, OrganizationEventPublisher events, Clock clock) {
		this.invitations = invitations;
		this.users = users;
		this.licenses = licenses;
		this.events = events;
		this.clock = clock;
	}

	/** Whether the company can take one more user: active users plus live pending invitations. */
	boolean hasSeatAvailable(UUID companyId) {
		License license = licenses.findFirst()
				.orElseThrow(() -> new OrganizationNotFoundException("License not configured"));
		long taken = users.countActiveByCompanyId(companyId)
				+ invitations.countPendingNotExpired(companyId, LocalDateTime.now(clock));
		return license.allowsAnotherUser(taken);
	}

	Invitation issue(String email, UUID companyId, Role role, String firstName, String lastName,
			String jobTitle, UUID createdByUserId) {
		LocalDateTime now = LocalDateTime.now(clock);
		Invitation invitation = Invitation.issue(email, companyId, role, newToken(), firstName,
				lastName, jobTitle, createdByUserId, now);
		if (users.findByEmail(invitation.email()).isPresent()) {
			throw new OrganizationConflictException("A user with this email already exists");
		}
		if (!hasSeatAvailable(companyId)) {
			throw new OrganizationConflictException(
					"The company has reached its user limit of the license");
		}
		Invitation saved = invitations.save(invitation);
		events.publishInvitationCreated(new InvitationCreatedEvent(saved.id(), saved.email(),
				saved.companyId(), saved.role().name(), saved.expiresAt(),
				saved.createdByUserId(), saved.token()));
		return saved;
	}

	/** 256 random bits, base64 url-safe without padding. */
	private String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
