package com.nodotextil.trazatex.organizationaccess.application;

import static com.nodotextil.trazatex.organizationaccess.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.InvitationStatus;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.support.FixedClocks;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryInvitationRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryLicenseRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryOrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.support.RecordingEventPublisher;
import com.nodotextil.trazatex.organizationaccess.support.TestData;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ExpirePendingInvitationsUseCaseTest {

	private final InMemoryInvitationRepository invitations = new InMemoryInvitationRepository();
	private final FixedClocks.Mutable clock = new FixedClocks.Mutable(NOW);
	private final ExpirePendingInvitationsUseCase useCase = new ExpirePendingInvitationsUseCase(
			invitations, clock);

	private final UUID companyId = UUID.randomUUID();

	private Invitation pending(String email) {
		return pendingCreatedAt(email, NOW);
	}

	private Invitation pendingCreatedAt(String email, java.time.LocalDateTime createdAt) {
		return invitations.save(Invitation.issue(email, companyId, Role.OPERATOR,
				"token-" + email, "Luis", "Perez", "Dyer", UUID.randomUUID(), createdAt));
	}

	private InvitationStatus statusOf(Invitation invitation) {
		return invitations.findById(invitation.id()).orElseThrow().status();
	}

	@Test
	void onDaySixTheInvitationIsStillPending() {
		Invitation invitation = pending("a@example.com");

		clock.advanceDays(6);

		assertThat(useCase.execute()).isZero();
		assertThat(statusOf(invitation)).isEqualTo(InvitationStatus.PENDING);
	}

	@Test
	void justBeforeTheSeventhDayItIsStillPending() {
		Invitation invitation = pending("a@example.com");

		clock.advanceMinutes(7 * 24 * 60 - 1);

		assertThat(useCase.execute()).isZero();
		assertThat(statusOf(invitation)).isEqualTo(InvitationStatus.PENDING);
	}

	@Test
	void onDaySevenItBecomesExpired() {
		Invitation invitation = pending("a@example.com");

		clock.advanceDays(7);

		assertThat(useCase.execute()).isEqualTo(1);
		assertThat(statusOf(invitation)).isEqualTo(InvitationStatus.EXPIRED);
	}

	@Test
	void acceptedAndCancelledInvitationsDoNotChange() {
		Invitation accepted = invitations.save(pending("a@example.com").accept());
		Invitation cancelled = invitations.save(pending("b@example.com").cancel());
		Invitation stillPending = pending("c@example.com");

		clock.advanceDays(30);

		assertThat(useCase.execute()).isEqualTo(1);
		assertThat(statusOf(accepted)).isEqualTo(InvitationStatus.ACCEPTED);
		assertThat(statusOf(cancelled)).isEqualTo(InvitationStatus.CANCELLED);
		assertThat(statusOf(stillPending)).isEqualTo(InvitationStatus.EXPIRED);
	}

	@Test
	void expiresOnlyTheOnesThatAreDue() {
		Invitation old = pending("old@example.com");
		clock.advanceDays(5);
		Invitation recent = pendingCreatedAt("recent@example.com", NOW.plusDays(5));

		clock.advanceDays(2);

		assertThat(useCase.execute()).isEqualTo(1);
		assertThat(statusOf(old)).isEqualTo(InvitationStatus.EXPIRED);
		assertThat(statusOf(recent)).isEqualTo(InvitationStatus.PENDING);
	}

	@Test
	void runningItAgainChangesNothing() {
		Invitation invitation = pending("a@example.com");
		clock.advanceDays(8);

		assertThat(useCase.execute()).isEqualTo(1);
		assertThat(useCase.execute()).isZero();
		assertThat(statusOf(invitation)).isEqualTo(InvitationStatus.EXPIRED);
	}

	@Test
	void expiredInvitationsStopTakingASeat() {
		var licenses = new InMemoryLicenseRepository();
		licenses.save(TestData.license(3, 1));
		var issuer = new InvitationIssuer(invitations, new InMemoryOrganizationUserRepository(),
				licenses, new RecordingEventPublisher(), clock);
		issuer.issue("a@example.com", companyId, Role.OPERATOR, "Luis", "Perez", "Dyer",
				UUID.randomUUID());
		assertThat(issuer.hasSeatAvailable(companyId)).isFalse();

		clock.advanceDays(7);
		useCase.execute();

		assertThat(issuer.hasSeatAvailable(companyId)).isTrue();
		assertThat(invitations.all()).extracting(Invitation::status)
				.containsExactly(InvitationStatus.EXPIRED);
	}
}
