package com.nodotextil.trazatex.organizationaccess.application;

import static com.nodotextil.trazatex.organizationaccess.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.nodotextil.trazatex.organizationaccess.application.DecideAdminRequestUseCase.Decision;
import com.nodotextil.trazatex.organizationaccess.domain.AdminRequest;
import com.nodotextil.trazatex.organizationaccess.domain.AdminRequestStatus;
import com.nodotextil.trazatex.organizationaccess.domain.Company;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.InvitationStatus;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationAccessDeniedException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationConflictException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationNotFoundException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.support.FixedClocks;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryAdminRequestRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryCompanyRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryInvitationRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryLicenseRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryOrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.support.RecordingEventPublisher;
import com.nodotextil.trazatex.organizationaccess.support.TestData;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AdminRequestUseCasesTest {

	private final InMemoryLicenseRepository licenses = new InMemoryLicenseRepository();
	private final InMemoryCompanyRepository companies = new InMemoryCompanyRepository();
	private final InMemoryOrganizationUserRepository users = new InMemoryOrganizationUserRepository();
	private final InMemoryInvitationRepository invitations = new InMemoryInvitationRepository();
	private final InMemoryAdminRequestRepository adminRequests = new InMemoryAdminRequestRepository();
	private final RecordingEventPublisher events = new RecordingEventPublisher();
	private final Clock clock = FixedClocks.at(NOW);
	private final InvitationIssuer issuer = new InvitationIssuer(invitations, users, licenses,
			events, clock);
	private final CreateAdminRequestUseCase create = new CreateAdminRequestUseCase(users,
			adminRequests, clock);
	private final DecideAdminRequestUseCase decide = new DecideAdminRequestUseCase(adminRequests,
			issuer, clock);

	private Company company;
	private OrganizationUser admin;
	private final UUID ownerId = UUID.randomUUID();

	private void setUp(int maxUsers) {
		var license = licenses.save(TestData.license(5, maxUsers));
		company = companies.save(TestData.company(license, "20123456789"));
		admin = users.save(TestData.user("admin@example.com", Role.COMPANY_ADMIN, company.id()));
	}

	private AdminRequest request() {
		return create.execute(admin.id(), "Rosa", "Diaz", "rosa@example.com", "Manager");
	}

	// ---- creating requests (RF-010)

	@Test
	void anAdministratorRequestsAnotherAdministratorForTheirCompany() {
		setUp(5);

		AdminRequest request = request();

		assertThat(request.status()).isEqualTo(AdminRequestStatus.PENDING);
		assertThat(request.companyId()).isEqualTo(company.id());
		assertThat(request.requestedByUserId()).isEqualTo(admin.id());
		assertThat(request.email()).isEqualTo("rosa@example.com");
		assertThat(request.firstName()).isEqualTo("Rosa");
		assertThat(request.lastName()).isEqualTo("Diaz");
		assertThat(request.jobTitle()).isEqualTo("Manager");
		assertThat(adminRequests.findById(request.id())).isPresent();
	}

	@Test
	void namesEmailAndJobTitleAreMandatory() {
		setUp(5);

		OrganizationValidationException all = catchThrowableOfType(
				OrganizationValidationException.class,
				() -> create.execute(admin.id(), null, "", " ", null));
		assertThat(all.fieldErrors()).containsOnlyKeys("firstName", "lastName", "email",
				"jobTitle");
		assertThatThrownBy(() -> create.execute(admin.id(), "Rosa", "Diaz", "rosa@example.com",
				null)).isInstanceOf(OrganizationValidationException.class);
		assertThatThrownBy(() -> create.execute(admin.id(), "Rosa", null, "rosa@example.com",
				"Manager")).isInstanceOf(OrganizationValidationException.class);
		assertThat(adminRequests.findAll()).isEmpty();
	}

	@Test
	void thereCannotBeTwoPendingRequestsForTheSameEmailAndCompany() {
		setUp(5);
		request();

		assertThatThrownBy(() -> create.execute(admin.id(), "Rosa", "Diaz", "ROSA@example.com",
				"Manager")).isInstanceOf(OrganizationConflictException.class)
				.hasMessageContaining("pending request");
		assertThat(adminRequests.findAll()).hasSize(1);
	}

	@Test
	void theSameEmailCanBeRequestedByAnotherCompanyOrAfterTheDecision() {
		setUp(5);
		AdminRequest first = request();
		var otherCompany = companies.save(TestData.company(licenses.findFirst().orElseThrow(),
				"20999999999"));
		OrganizationUser otherAdmin = users.save(TestData.user("other@example.com",
				Role.COMPANY_ADMIN, otherCompany.id()));
		assertThat(create.execute(otherAdmin.id(), "Rosa", "Diaz", "rosa@example.com",
				"Manager")).isNotNull();

		decide.execute(first.id(), false, ownerId);

		assertThat(request().status()).isEqualTo(AdminRequestStatus.PENDING);
	}

	@Test
	void anEmailThatAlreadyHasAnAccountCannotBeRequested() {
		setUp(5);

		assertThatThrownBy(() -> create.execute(admin.id(), "Ana", "Lopez", "admin@example.com",
				"Boss")).isInstanceOf(OrganizationConflictException.class);
	}

	@Test
	void onlyActiveCompanyAdministratorsCanRequest() {
		setUp(5);
		OrganizationUser operator = users.save(TestData.user("op@example.com", Role.OPERATOR,
				company.id()));

		assertThatThrownBy(() -> create.execute(operator.id(), "Rosa", "Diaz",
				"rosa@example.com", "Manager")).isInstanceOf(OrganizationAccessDeniedException.class);
	}

	// ---- deciding (RF-011)

	@Test
	void approvingCreatesTheAdministratorInvitationInheritingTheRequestData() {
		setUp(5);
		AdminRequest request = request();

		Decision decision = decide.execute(request.id(), true, ownerId);

		assertThat(decision.request().status()).isEqualTo(AdminRequestStatus.APPROVED);
		assertThat(decision.request().decidedByUserId()).isEqualTo(ownerId);
		assertThat(decision.request().decidedAt()).isEqualTo(NOW);
		Invitation invitation = decision.invitation().orElseThrow();
		assertThat(invitation.role()).isEqualTo(Role.COMPANY_ADMIN);
		assertThat(invitation.email()).isEqualTo("rosa@example.com");
		assertThat(invitation.companyId()).isEqualTo(company.id());
		assertThat(invitation.firstName()).isEqualTo("Rosa");
		assertThat(invitation.lastName()).isEqualTo("Diaz");
		assertThat(invitation.jobTitle()).isEqualTo("Manager");
		assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);
		assertThat(invitation.createdByUserId()).isEqualTo(ownerId);
		assertThat(events.invitationCreated).hasSize(1);
		assertThat(events.invitationCreated.get(0).role()).isEqualTo("COMPANY_ADMIN");
	}

	@Test
	void approvingWithoutASeatFailsAndLeavesTheRequestPending() {
		setUp(1); // the existing administrator already takes the only seat
		AdminRequest request = request();

		assertThatThrownBy(() -> decide.execute(request.id(), true, ownerId))
				.isInstanceOf(OrganizationConflictException.class)
				.hasMessageContaining("user limit");

		assertThat(adminRequests.findById(request.id()).orElseThrow().status())
				.isEqualTo(AdminRequestStatus.PENDING);
		assertThat(invitations.all()).isEmpty();
		assertThat(events.invitationCreated).isEmpty();
	}

	@Test
	void rejectingCreatesNoInvitation() {
		setUp(5);
		AdminRequest request = request();

		Decision decision = decide.execute(request.id(), false, ownerId);

		assertThat(decision.request().status()).isEqualTo(AdminRequestStatus.REJECTED);
		assertThat(decision.invitation()).isEmpty();
		assertThat(invitations.all()).isEmpty();
	}

	@Test
	void aRequestIsDecidedOnlyOnce() {
		setUp(5);
		AdminRequest request = request();
		decide.execute(request.id(), true, ownerId);

		assertThatThrownBy(() -> decide.execute(request.id(), true, ownerId))
				.isInstanceOf(OrganizationConflictException.class);
		assertThatThrownBy(() -> decide.execute(request.id(), false, ownerId))
				.isInstanceOf(OrganizationConflictException.class);
		assertThat(invitations.all()).hasSize(1);
	}

	@Test
	void unknownRequestIsNotFound() {
		setUp(5);

		assertThatThrownBy(() -> decide.execute(UUID.randomUUID(), true, ownerId))
				.isInstanceOf(OrganizationNotFoundException.class);
	}

	@Test
	void theOwnerListsRequestsOfEveryCompanyWithAnOptionalStatusFilter() {
		setUp(5);
		AdminRequest pending = request();
		AdminRequest rejected = create.execute(admin.id(), "Luis", "Perez", "luis@example.com",
				"Boss");
		decide.execute(rejected.id(), false, ownerId);
		var list = new ListAdminRequestsUseCase(adminRequests, companies);

		assertThat(list.execute(null)).hasSize(2);
		List<ListAdminRequestsUseCase.AdminRequestView> onlyPending = list
				.execute(AdminRequestStatus.PENDING);
		assertThat(onlyPending).hasSize(1);
		assertThat(onlyPending.get(0).request().id()).isEqualTo(pending.id());
		assertThat(onlyPending.get(0).companyLegalName()).isEqualTo(company.legalName());
	}
}
