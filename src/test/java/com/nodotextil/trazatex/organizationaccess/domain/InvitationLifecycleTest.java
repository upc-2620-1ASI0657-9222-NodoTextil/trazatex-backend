package com.nodotextil.trazatex.organizationaccess.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class InvitationLifecycleTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 9, 0);

	private Invitation pending() {
		return Invitation.issue("op@example.com", UUID.randomUUID(), Role.OPERATOR, "token",
				"Luis", "Perez", "Dyer", UUID.randomUUID(), NOW);
	}

	@Test
	void issuedInvitationIsPendingAndValidForSevenDays() {
		Invitation invitation = pending();

		assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);
		assertThat(invitation.expiresAt()).isEqualTo(NOW.plusDays(7));
	}

	@Test
	void isUsableUntilTheExpiryInstant() {
		Invitation invitation = pending();

		assertThat(invitation.isUsableAt(NOW.plusDays(6))).isTrue();
		assertThat(invitation.isUsableAt(NOW.plusDays(7).minusSeconds(1))).isTrue();
		assertThat(invitation.isUsableAt(NOW.plusDays(7))).isFalse();
		assertThat(invitation.effectiveStatusAt(NOW.plusDays(7)))
				.isEqualTo(InvitationStatus.EXPIRED);
		assertThat(invitation.effectiveStatusAt(NOW.plusDays(6)))
				.isEqualTo(InvitationStatus.PENDING);
	}

	@Test
	void aPendingInvitationCanBeAcceptedCancelledOrExpired() {
		assertThat(pending().accept().status()).isEqualTo(InvitationStatus.ACCEPTED);
		assertThat(pending().cancel().status()).isEqualTo(InvitationStatus.CANCELLED);
		assertThat(pending().expire().status()).isEqualTo(InvitationStatus.EXPIRED);
	}

	@Test
	void finalStatesNeverChange() {
		Invitation accepted = pending().accept();
		Invitation cancelled = pending().cancel();
		Invitation expired = pending().expire();

		for (Invitation finalState : new Invitation[] { accepted, cancelled, expired }) {
			assertThatThrownBy(finalState::accept)
					.isInstanceOf(OrganizationConflictException.class);
			assertThatThrownBy(finalState::cancel)
					.isInstanceOf(OrganizationConflictException.class);
			assertThatThrownBy(finalState::expire)
					.isInstanceOf(OrganizationConflictException.class);
		}
		assertThat(cancelled.effectiveStatusAt(NOW.plusDays(30)))
				.isEqualTo(InvitationStatus.CANCELLED);
		assertThat(accepted.isUsableAt(NOW)).isFalse();
	}

	@Test
	void transitionsKeepTheRestOfTheData() {
		Invitation original = pending();

		Invitation accepted = original.accept();

		assertThat(accepted.id()).isEqualTo(original.id());
		assertThat(accepted.token()).isEqualTo(original.token());
		assertThat(accepted.firstName()).isEqualTo("Luis");
		assertThat(accepted.jobTitle()).isEqualTo("Dyer");
		assertThat(accepted.expiresAt()).isEqualTo(original.expiresAt());
	}
}
