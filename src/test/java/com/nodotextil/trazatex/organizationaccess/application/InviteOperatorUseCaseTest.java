package com.nodotextil.trazatex.organizationaccess.application;

import static com.nodotextil.trazatex.organizationaccess.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.InvitationStatus;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationAccessDeniedException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationConflictException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.domain.UserStatus;
import com.nodotextil.trazatex.organizationaccess.support.FixedClocks;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryInvitationRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryLicenseRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryOrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.support.RecordingEventPublisher;
import com.nodotextil.trazatex.organizationaccess.support.TestData;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class InviteOperatorUseCaseTest {

	private final InMemoryInvitationRepository invitations = new InMemoryInvitationRepository();
	private final InMemoryOrganizationUserRepository users = new InMemoryOrganizationUserRepository();
	private final InMemoryLicenseRepository licenses = new InMemoryLicenseRepository();
	private final RecordingEventPublisher events = new RecordingEventPublisher();
	private final InviteOperatorUseCase useCase = new InviteOperatorUseCase(users,
			new InvitationIssuer(invitations, users, licenses, events, FixedClocks.at(NOW)));

	private final UUID companyId = UUID.randomUUID();

	private OrganizationUser admin() {
		return users.save(TestData.user("admin@example.com", Role.COMPANY_ADMIN, companyId));
	}

	@Test
	void anAdministratorInvitesAnOperatorToTheirOwnCompany() {
		licenses.save(TestData.license(3, 5));
		OrganizationUser admin = admin();

		Invitation invitation = useCase.execute(admin.id(), "Luis", "Perez", "Luis@Example.com",
				"Dyer");

		assertThat(invitation.role()).isEqualTo(Role.OPERATOR);
		assertThat(invitation.companyId()).isEqualTo(companyId);
		assertThat(invitation.email()).isEqualTo("luis@example.com");
		assertThat(invitation.firstName()).isEqualTo("Luis");
		assertThat(invitation.lastName()).isEqualTo("Perez");
		assertThat(invitation.jobTitle()).isEqualTo("Dyer");
		assertThat(invitation.createdByUserId()).isEqualTo(admin.id());
		assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);
		assertThat(events.invitationCreated).hasSize(1);
		assertThat(events.invitationCreated.get(0).role()).isEqualTo("OPERATOR");
		assertThat(events.invitationCreated.get(0).createdByUserId()).isEqualTo(admin.id());
	}

	@Test
	void firstNameLastNameEmailAndJobTitleAreMandatory() {
		licenses.save(TestData.license(3, 5));
		UUID adminId = admin().id();

		OrganizationValidationException error = catchThrowableOfType(
				OrganizationValidationException.class,
				() -> useCase.execute(adminId, null, " ", "", null));

		assertThat(error).isNotNull();
		assertThat(error.fieldErrors()).containsOnlyKeys("firstName", "lastName", "email",
				"jobTitle");
		assertThat(invitations.all()).isEmpty();
		assertThat(events.invitationCreated).isEmpty();
	}

	@Test
	void eachMissingFieldIsRejectedOnItsOwn() {
		licenses.save(TestData.license(3, 5));
		UUID adminId = admin().id();

		assertMissing(adminId, null, "Perez", "a@example.com", "Dyer", "firstName");
		assertMissing(adminId, "Luis", null, "a@example.com", "Dyer", "lastName");
		assertMissing(adminId, "Luis", "Perez", null, "Dyer", "email");
		assertMissing(adminId, "Luis", "Perez", "a@example.com", " ", "jobTitle");
	}

	private void assertMissing(UUID adminId, String first, String last, String email,
			String job, String field) {
		OrganizationValidationException error = catchThrowableOfType(
				OrganizationValidationException.class,
				() -> useCase.execute(adminId, first, last, email, job));
		assertThat(error).isNotNull();
		assertThat(error.fieldErrors()).containsOnlyKeys(field);
	}

	@Test
	void rejectsAnInvitationWhenThereIsNoSeatLeft() {
		licenses.save(TestData.license(3, 2));
		OrganizationUser admin = admin();
		useCase.execute(admin.id(), "Luis", "Perez", "luis@example.com", "Dyer");

		assertThatThrownBy(() -> useCase.execute(admin.id(), "Rosa", "Diaz", "rosa@example.com",
				"Weaver")).isInstanceOf(OrganizationConflictException.class)
				.hasMessageContaining("user limit");
		assertThat(invitations.all()).hasSize(1);
		assertThat(events.invitationCreated).hasSize(1);
	}

	@Test
	void onlyAnActiveCompanyAdministratorCanInvite() {
		licenses.save(TestData.license(3, 5));
		OrganizationUser operator = users.save(TestData.user("op@example.com", Role.OPERATOR,
				companyId));
		OrganizationUser owner = users.save(TestData.user("owner@example.com",
				Role.LICENSE_OWNER, null));
		OrganizationUser inactiveAdmin = users.save(new OrganizationUser(UUID.randomUUID(),
				"old@example.com", "Old", "Admin", null, "hash", Role.COMPANY_ADMIN, companyId,
				UserStatus.INACTIVE, 0, null, NOW));

		for (UUID actor : new UUID[] { operator.id(), owner.id(), inactiveAdmin.id(),
				UUID.randomUUID() }) {
			assertThatThrownBy(() -> useCase.execute(actor, "Luis", "Perez", "luis@example.com",
					"Dyer")).isInstanceOf(OrganizationAccessDeniedException.class);
		}
		assertThat(invitations.all()).isEmpty();
	}
}
