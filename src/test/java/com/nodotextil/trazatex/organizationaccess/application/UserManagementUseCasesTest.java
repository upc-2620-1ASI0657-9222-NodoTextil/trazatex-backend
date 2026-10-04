package com.nodotextil.trazatex.organizationaccess.application;

import static com.nodotextil.trazatex.organizationaccess.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodotextil.trazatex.organizationaccess.domain.Company;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationAccessDeniedException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationConflictException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationNotFoundException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.domain.UserStatus;
import com.nodotextil.trazatex.organizationaccess.support.FixedClocks;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryCompanyRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryInvitationRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryLicenseRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryOrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.support.RecordingEventPublisher;
import com.nodotextil.trazatex.organizationaccess.support.TestData;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class UserManagementUseCasesTest {

	private final InMemoryLicenseRepository licenses = new InMemoryLicenseRepository();
	private final InMemoryCompanyRepository companies = new InMemoryCompanyRepository();
	private final InMemoryOrganizationUserRepository users = new InMemoryOrganizationUserRepository();
	private final InMemoryInvitationRepository invitations = new InMemoryInvitationRepository();
	private final InvitationIssuer issuer = new InvitationIssuer(invitations, users, licenses,
			new RecordingEventPublisher(), FixedClocks.at(NOW));
	private final ChangeUserStatusUseCase changeStatus = new ChangeUserStatusUseCase(users,
			issuer);
	private final ListCompanyUsersUseCase listUsers = new ListCompanyUsersUseCase(users);

	private Company company;
	private OrganizationUser admin;

	private void setUp(int maxUsers) {
		var license = licenses.save(TestData.license(5, maxUsers));
		company = companies.save(TestData.company(license, "20123456789"));
		admin = users.save(TestData.user("admin@example.com", Role.COMPANY_ADMIN, company.id()));
	}

	private OrganizationUser operator(String email, UUID companyId, UserStatus status) {
		return users.save(new OrganizationUser(UUID.randomUUID(), email, "Op", "Erator", null,
				"hash", Role.OPERATOR, companyId, status, 0, null, NOW));
	}

	@Test
	void listsOnlyTheUsersOfTheAdministratorsOwnCompany() {
		setUp(10);
		OrganizationUser mine = operator("mine@example.com", company.id(), UserStatus.ACTIVE);
		OrganizationUser inactive = operator("old@example.com", company.id(),
				UserStatus.INACTIVE);
		operator("other@example.com", UUID.randomUUID(), UserStatus.ACTIVE);
		users.save(TestData.user("owner@example.com", Role.LICENSE_OWNER, null));

		assertThat(listUsers.execute(admin.id())).containsExactly(admin, mine, inactive);
	}

	@Test
	void anAdministratorInactivatesAUserOfTheirCompany() {
		setUp(10);
		OrganizationUser operator = operator("op@example.com", company.id(), UserStatus.ACTIVE);

		OrganizationUser result = changeStatus.execute(admin.id(), operator.id(),
				UserStatus.INACTIVE);

		assertThat(result.status()).isEqualTo(UserStatus.INACTIVE);
		assertThat(users.findById(operator.id()).orElseThrow().isActive()).isFalse();
	}

	@Test
	void inactivatingNeverChecksTheSeats() {
		setUp(1); // the administrator alone fills the license
		OrganizationUser operator = operator("op@example.com", company.id(), UserStatus.ACTIVE);

		assertThat(changeStatus.execute(admin.id(), operator.id(), UserStatus.INACTIVE).status())
				.isEqualTo(UserStatus.INACTIVE);
	}

	@Test
	void reactivatingWithoutASeatIsRejected() {
		setUp(2);
		OrganizationUser inactive = operator("old@example.com", company.id(),
				UserStatus.INACTIVE);
		operator("new@example.com", company.id(), UserStatus.ACTIVE); // the seats are full

		assertThatThrownBy(() -> changeStatus.execute(admin.id(), inactive.id(),
				UserStatus.ACTIVE)).isInstanceOf(OrganizationConflictException.class)
				.hasMessageContaining("user limit");
		assertThat(users.findById(inactive.id()).orElseThrow().status())
				.isEqualTo(UserStatus.INACTIVE);
	}

	@Test
	void aLivePendingInvitationAlsoHoldsTheSeat() {
		setUp(2);
		OrganizationUser inactive = operator("old@example.com", company.id(),
				UserStatus.INACTIVE);
		issuer.issue("invited@example.com", company.id(), Role.OPERATOR, "In", "Vited", "Job",
				admin.id());

		assertThatThrownBy(() -> changeStatus.execute(admin.id(), inactive.id(),
				UserStatus.ACTIVE)).isInstanceOf(OrganizationConflictException.class);
	}

	@Test
	void reactivatingWithASeatWorks() {
		setUp(3);
		OrganizationUser inactive = operator("old@example.com", company.id(),
				UserStatus.INACTIVE);

		OrganizationUser result = changeStatus.execute(admin.id(), inactive.id(),
				UserStatus.ACTIVE);

		assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
		assertThat(users.findById(inactive.id()).orElseThrow().isActive()).isTrue();
	}

	@Test
	void settingTheCurrentStatusAgainChangesNothingAndNeedsNoSeat() {
		setUp(2);
		OrganizationUser active = operator("a@example.com", company.id(), UserStatus.ACTIVE);

		assertThat(changeStatus.execute(admin.id(), active.id(), UserStatus.ACTIVE))
				.isEqualTo(active);
	}

	@Test
	void aUserNeverGoesBackToPending() {
		setUp(5);
		OrganizationUser operator = operator("op@example.com", company.id(), UserStatus.ACTIVE);

		assertThatThrownBy(() -> changeStatus.execute(admin.id(), operator.id(),
				UserStatus.PENDING)).isInstanceOf(OrganizationValidationException.class);
		assertThat(users.findById(operator.id()).orElseThrow().status())
				.isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	void usersOfAnotherCompanyCannotBeTouched() {
		setUp(5);
		OrganizationUser foreign = operator("foreign@example.com", UUID.randomUUID(),
				UserStatus.ACTIVE);

		assertThatThrownBy(() -> changeStatus.execute(admin.id(), foreign.id(),
				UserStatus.INACTIVE)).isInstanceOf(OrganizationAccessDeniedException.class);
		assertThat(users.findById(foreign.id()).orElseThrow().isActive()).isTrue();
	}

	@Test
	void theLicenseOwnerCannotBeTouched() {
		setUp(5);
		OrganizationUser owner = users.save(TestData.user("owner@example.com",
				Role.LICENSE_OWNER, null));

		assertThatThrownBy(() -> changeStatus.execute(admin.id(), owner.id(),
				UserStatus.INACTIVE)).isInstanceOf(OrganizationAccessDeniedException.class);
		assertThat(users.findById(owner.id()).orElseThrow().isActive()).isTrue();
	}

	@Test
	void unknownUserIsNotFound() {
		setUp(5);

		assertThatThrownBy(() -> changeStatus.execute(admin.id(), UUID.randomUUID(),
				UserStatus.INACTIVE)).isInstanceOf(OrganizationNotFoundException.class);
	}

	@Test
	void onlyActiveCompanyAdministratorsCanListOrChangeUsers() {
		setUp(5);
		OrganizationUser operator = operator("op@example.com", company.id(), UserStatus.ACTIVE);
		OrganizationUser target = operator("t@example.com", company.id(), UserStatus.ACTIVE);
		OrganizationUser inactiveAdmin = users.save(new OrganizationUser(UUID.randomUUID(),
				"old-admin@example.com", "Old", "Admin", null, "hash", Role.COMPANY_ADMIN,
				company.id(), UserStatus.INACTIVE, 0, null, NOW));
		OrganizationUser owner = users.save(TestData.user("owner@example.com",
				Role.LICENSE_OWNER, null));

		for (UUID actor : new UUID[] { operator.id(), inactiveAdmin.id(), owner.id(),
				UUID.randomUUID() }) {
			assertThatThrownBy(() -> listUsers.execute(actor))
					.isInstanceOf(OrganizationAccessDeniedException.class);
			assertThatThrownBy(() -> changeStatus.execute(actor, target.id(),
					UserStatus.INACTIVE)).isInstanceOf(OrganizationAccessDeniedException.class);
		}
	}
}
