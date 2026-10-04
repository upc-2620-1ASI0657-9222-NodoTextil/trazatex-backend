package com.nodotextil.trazatex.organizationaccess.application;

import static com.nodotextil.trazatex.organizationaccess.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodotextil.trazatex.organizationaccess.application.GetInvitationUseCase.InvitationView;
import com.nodotextil.trazatex.organizationaccess.domain.Company;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.InvitationStatus;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationAccessDeniedException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationConflictException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationNotFoundException;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.support.FixedClocks;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryCompanyRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryInvitationRepository;
import com.nodotextil.trazatex.organizationaccess.support.TestData;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CancelAndGetInvitationTest {

	private final InMemoryInvitationRepository invitations = new InMemoryInvitationRepository();
	private final InMemoryCompanyRepository companies = new InMemoryCompanyRepository();
	private final FixedClocks.Mutable clock = new FixedClocks.Mutable(NOW);
	private final CancelInvitationUseCase cancel = new CancelInvitationUseCase(invitations, clock);
	private final GetInvitationUseCase get = new GetInvitationUseCase(invitations, companies,
			clock);

	private final UUID issuerId = UUID.randomUUID();
	private final UUID companyId = UUID.randomUUID();

	private Invitation saved(Invitation invitation) {
		return invitations.save(invitation);
	}

	private Invitation pending() {
		return saved(Invitation.issue("op@example.com", companyId, Role.OPERATOR, "tok-" + UUID.randomUUID(),
				"Luis", "Perez", "Dyer", issuerId, NOW));
	}

	@Test
	void theIssuerCancelsAPendingInvitation() {
		Invitation invitation = pending();

		Invitation cancelled = cancel.execute(invitation.id(), issuerId);

		assertThat(cancelled.status()).isEqualTo(InvitationStatus.CANCELLED);
		assertThat(invitations.findById(invitation.id()).orElseThrow().status())
				.isEqualTo(InvitationStatus.CANCELLED);
	}

	@Test
	void someoneElseCannotCancelIt() {
		Invitation invitation = pending();

		assertThatThrownBy(() -> cancel.execute(invitation.id(), UUID.randomUUID()))
				.isInstanceOf(OrganizationAccessDeniedException.class);
		assertThat(invitations.findById(invitation.id()).orElseThrow().status())
				.isEqualTo(InvitationStatus.PENDING);
	}

	@Test
	void onlyAPendingInvitationCanBeCancelled() {
		Invitation accepted = saved(pending().accept());
		Invitation cancelled = saved(pending().cancel());
		Invitation expired = saved(pending().expire());

		for (Invitation invitation : new Invitation[] { accepted, cancelled, expired }) {
			assertThatThrownBy(() -> cancel.execute(invitation.id(), issuerId))
					.isInstanceOf(OrganizationConflictException.class);
		}
	}

	@Test
	void anInvitationPastItsExpiryCannotBeCancelledEvenIfTheJobHasNotRunYet() {
		Invitation invitation = pending();
		clock.advanceDays(8);

		assertThatThrownBy(() -> cancel.execute(invitation.id(), issuerId))
				.isInstanceOf(OrganizationConflictException.class);
	}

	@Test
	void cancellingAnUnknownInvitationIsNotFound() {
		assertThatThrownBy(() -> cancel.execute(UUID.randomUUID(), issuerId))
				.isInstanceOf(OrganizationNotFoundException.class);
	}

	@Test
	void theTokenShowsTheDataToPrefillTheForm() {
		var license = TestData.license(3, 5);
		Company company = companies.save(new Company(companyId, license.id(), "20123456789",
				"Hilandería Andina SAC", java.util.Set.of("SPINNING"),
				com.nodotextil.trazatex.organizationaccess.domain.CompanyStatus.ACTIVE, NOW));
		Invitation invitation = pending();

		InvitationView view = get.execute(invitation.token());

		assertThat(view.invitation().email()).isEqualTo("op@example.com");
		assertThat(view.invitation().role()).isEqualTo(Role.OPERATOR);
		assertThat(view.invitation().firstName()).isEqualTo("Luis");
		assertThat(view.invitation().lastName()).isEqualTo("Perez");
		assertThat(view.invitation().jobTitle()).isEqualTo("Dyer");
		assertThat(view.companyLegalName()).isEqualTo(company.legalName());
		assertThat(view.invitation().expiresAt()).isEqualTo(NOW.plusDays(7));
		assertThat(view.status()).isEqualTo(InvitationStatus.PENDING);
	}

	@Test
	void anUnknownTokenIsNotFound() {
		assertThatThrownBy(() -> get.execute("does-not-exist"))
				.isInstanceOf(OrganizationNotFoundException.class);
	}

	@Test
	void anInvitationPastItsExpiryIsShownAsExpiredBeforeTheJobRuns() {
		Invitation invitation = pending();
		clock.advanceDays(7);

		assertThat(get.execute(invitation.token()).status()).isEqualTo(InvitationStatus.EXPIRED);
	}

	@Test
	void finalStatusesAreShownAsTheyAre() {
		Invitation cancelled = saved(pending().cancel());

		assertThat(get.execute(cancelled.token()).status()).isEqualTo(InvitationStatus.CANCELLED);
	}
}
