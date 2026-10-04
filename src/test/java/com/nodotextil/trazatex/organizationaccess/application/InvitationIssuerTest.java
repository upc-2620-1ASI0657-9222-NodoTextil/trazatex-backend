package com.nodotextil.trazatex.organizationaccess.application;

import static com.nodotextil.trazatex.organizationaccess.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodotextil.trazatex.organizationaccess.domain.Company;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.InvitationStatus;
import com.nodotextil.trazatex.organizationaccess.domain.License;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationConflictException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationNotFoundException;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.support.FixedClocks;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryInvitationRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryLicenseRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryOrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.support.RecordingEventPublisher;
import com.nodotextil.trazatex.organizationaccess.support.TestData;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class InvitationIssuerTest {

	private final InMemoryInvitationRepository invitations = new InMemoryInvitationRepository();
	private final InMemoryOrganizationUserRepository users = new InMemoryOrganizationUserRepository();
	private final InMemoryLicenseRepository licenses = new InMemoryLicenseRepository();
	private final RecordingEventPublisher events = new RecordingEventPublisher();
	private final FixedClocks.Mutable clock = new FixedClocks.Mutable(NOW);
	private final InvitationIssuer issuer = new InvitationIssuer(invitations, users, licenses,
			events, clock);

	private final UUID companyId = UUID.randomUUID();
	private final UUID actorId = UUID.randomUUID();

	private License withLicense(int maxUsers) {
		return licenses.save(TestData.license(5, maxUsers));
	}

	@Test
	void createsAPendingInvitationThatExpiresInSevenDays() {
		withLicense(5);

		Invitation invitation = issuer.issue("New@Example.com", companyId, Role.OPERATOR, "Ana",
				"Lopez", "Dyer", actorId);

		assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);
		assertThat(invitation.email()).isEqualTo("new@example.com");
		assertThat(invitation.createdAt()).isEqualTo(NOW);
		assertThat(invitation.expiresAt()).isEqualTo(NOW.plusDays(7));
		assertThat(invitation.createdByUserId()).isEqualTo(actorId);
		assertThat(invitation.firstName()).isEqualTo("Ana");
		assertThat(invitation.jobTitle()).isEqualTo("Dyer");
		assertThat(invitations.findById(invitation.id())).contains(invitation);
	}

	@Test
	void tokenIs256BitsBase64UrlSafeAndUnique() {
		withLicense(50);
		Set<String> tokens = new HashSet<>();

		for (int i = 0; i < 20; i++) {
			tokens.add(issuer.issue("user" + i + "@example.com", companyId, Role.OPERATOR, null,
					null, null, actorId).token());
		}

		assertThat(tokens).hasSize(20);
		for (String token : tokens) {
			assertThat(token).matches("[A-Za-z0-9_-]{43}");
			assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
		}
	}

	@Test
	void publishesInvitationCreatedWithTheTokenAndExpiry() {
		withLicense(5);

		Invitation invitation = issuer.issue("a@example.com", companyId, Role.COMPANY_ADMIN,
				null, null, null, actorId);

		assertThat(events.invitationCreated).hasSize(1);
		var event = events.invitationCreated.get(0);
		assertThat(event.invitationId()).isEqualTo(invitation.id());
		assertThat(event.aggregateId()).isEqualTo(invitation.id());
		assertThat(event.email()).isEqualTo("a@example.com");
		assertThat(event.companyId()).isEqualTo(companyId);
		assertThat(event.role()).isEqualTo("COMPANY_ADMIN");
		assertThat(event.expiresAt()).isEqualTo(NOW.plusDays(7));
		assertThat(event.createdByUserId()).isEqualTo(actorId);
		assertThat(event.token()).isEqualTo(invitation.token());
	}

	@Test
	void activeUsersAndLivePendingInvitationsTakeSeats() {
		withLicense(3);
		users.save(TestData.user("u1@example.com", Role.COMPANY_ADMIN, companyId));
		issuer.issue("i1@example.com", companyId, Role.OPERATOR, null, null, null, actorId);
		assertThat(issuer.hasSeatAvailable(companyId)).isTrue();

		issuer.issue("i2@example.com", companyId, Role.OPERATOR, null, null, null, actorId);

		assertThat(issuer.hasSeatAvailable(companyId)).isFalse();
		assertThatThrownBy(() -> issuer.issue("i3@example.com", companyId, Role.OPERATOR, null,
				null, null, actorId)).isInstanceOf(OrganizationConflictException.class)
				.hasMessageContaining("user limit");
		assertThat(events.invitationCreated).hasSize(2);
	}

	@Test
	void expiredPendingInvitationsDoNotTakeSeats() {
		withLicense(1);
		issuer.issue("i1@example.com", companyId, Role.OPERATOR, null, null, null, actorId);
		assertThat(issuer.hasSeatAvailable(companyId)).isFalse();

		clock.advanceDays(7);

		assertThat(issuer.hasSeatAvailable(companyId)).isTrue();
	}

	@Test
	void seatsAreCountedPerCompany() {
		withLicense(1);
		issuer.issue("i1@example.com", companyId, Role.OPERATOR, null, null, null, actorId);

		assertThat(issuer.hasSeatAvailable(UUID.randomUUID())).isTrue();
	}

	@Test
	void rejectsAnEmailThatAlreadyBelongsToAUser() {
		withLicense(5);
		users.save(TestData.user("taken@example.com", Role.OPERATOR, UUID.randomUUID()));

		assertThatThrownBy(() -> issuer.issue("Taken@Example.com", companyId, Role.OPERATOR,
				null, null, null, actorId)).isInstanceOf(OrganizationConflictException.class);
		assertThat(events.invitationCreated).isEmpty();
	}

	@Test
	void requiresAConfiguredLicense() {
		assertThatThrownBy(() -> issuer.issue("a@example.com", companyId, Role.OPERATOR, null,
				null, null, actorId)).isInstanceOf(OrganizationNotFoundException.class);
	}
}
