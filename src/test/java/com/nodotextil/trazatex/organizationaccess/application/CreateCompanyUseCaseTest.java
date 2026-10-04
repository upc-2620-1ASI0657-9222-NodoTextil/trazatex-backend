package com.nodotextil.trazatex.organizationaccess.application;

import static com.nodotextil.trazatex.organizationaccess.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.nodotextil.trazatex.organizationaccess.application.CreateCompanyUseCase.CreatedCompany;
import com.nodotextil.trazatex.organizationaccess.application.port.TaxpayerValidationPort;
import com.nodotextil.trazatex.organizationaccess.domain.CompanyStatus;
import com.nodotextil.trazatex.organizationaccess.domain.ExternalServiceUnavailableException;
import com.nodotextil.trazatex.organizationaccess.domain.InvitationStatus;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationConflictException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationNotFoundException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.support.FixedClocks;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryCompanyRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryInvitationRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryLicenseRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryOrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.support.RecordingEventPublisher;
import com.nodotextil.trazatex.organizationaccess.support.TestData;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateCompanyUseCaseTest {

	private static final String RUC = "20123456789";

	private final InMemoryLicenseRepository licenses = new InMemoryLicenseRepository();
	private final InMemoryCompanyRepository companies = new InMemoryCompanyRepository();
	private final InMemoryInvitationRepository invitations = new InMemoryInvitationRepository();
	private final InMemoryOrganizationUserRepository users = new InMemoryOrganizationUserRepository();
	private final RecordingEventPublisher events = new RecordingEventPublisher();
	private final AtomicInteger sunatCalls = new AtomicInteger();
	private boolean rucIsValid = true;
	private boolean sunatDown = false;
	private final TaxpayerValidationPort sunat = ruc -> {
		sunatCalls.incrementAndGet();
		if (sunatDown) {
			throw new ExternalServiceUnavailableException("SUNAT validation is unavailable");
		}
		return rucIsValid;
	};
	private final UUID ownerId = UUID.randomUUID();
	private CreateCompanyUseCase useCase;

	@BeforeEach
	void setUp() {
		var clock = FixedClocks.at(NOW);
		var issuer = new InvitationIssuer(invitations, users, licenses, events, clock);
		useCase = new CreateCompanyUseCase(licenses, companies, sunat, issuer, clock);
	}

	@Test
	void createsTheCompanyAndInvitesItsFirstAdministratorWithJustAnEmail() {
		var license = licenses.save(TestData.license(3, 10));

		CreatedCompany created = useCase.execute(" Hilandería Andina SAC ", RUC,
				Set.of("SPINNING", "DYEING"), "Admin@Andina.pe", ownerId);

		assertThat(created.company().licenseId()).isEqualTo(license.id());
		assertThat(created.company().legalName()).isEqualTo("Hilandería Andina SAC");
		assertThat(created.company().ruc()).isEqualTo(RUC);
		assertThat(created.company().activities()).containsExactlyInAnyOrder("SPINNING", "DYEING");
		assertThat(created.company().status()).isEqualTo(CompanyStatus.ACTIVE);
		assertThat(companies.findById(created.company().id())).isPresent();

		var invitation = created.adminInvitation();
		assertThat(invitation.role()).isEqualTo(Role.COMPANY_ADMIN);
		assertThat(invitation.email()).isEqualTo("admin@andina.pe");
		assertThat(invitation.companyId()).isEqualTo(created.company().id());
		assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);
		assertThat(invitation.createdByUserId()).isEqualTo(ownerId);
		assertThat(invitation.firstName()).isNull();
		assertThat(invitation.lastName()).isNull();
		assertThat(invitation.jobTitle()).isNull();
		assertThat(events.invitationCreated).hasSize(1);
		assertThat(events.invitationCreated.get(0).role()).isEqualTo("COMPANY_ADMIN");
	}

	@Test
	void rejectsWhenTheLicenseCompanyLimitWasReached() {
		var license = licenses.save(TestData.license(1, 10));
		companies.save(TestData.company(license, "20999999999"));

		assertThatThrownBy(() -> useCase.execute("Another SAC", RUC, Set.of("WEAVING"),
				"admin@example.com", ownerId)).isInstanceOf(OrganizationConflictException.class)
				.hasMessageContaining("limit of 1 companies");

		assertThat(companies.findAll()).hasSize(1);
		assertThat(sunatCalls).hasValue(0);
		assertThat(events.invitationCreated).isEmpty();
	}

	@Test
	void rejectsARucThatSunatDoesNotAccept() {
		licenses.save(TestData.license(3, 10));
		rucIsValid = false;

		OrganizationValidationException error = catchThrowableOfType(
				OrganizationValidationException.class, () -> useCase.execute("Textiles SAC", RUC,
						Set.of("WEAVING"), "admin@example.com", ownerId));

		assertThat(error).isNotNull();
		assertThat(error.fieldErrors()).containsKey("ruc");
		assertThat(companies.findAll()).isEmpty();
		assertThat(events.invitationCreated).isEmpty();
	}

	@Test
	void rejectsAMalformedRucWithoutCallingSunat() {
		licenses.save(TestData.license(3, 10));

		assertThatThrownBy(() -> useCase.execute("Textiles SAC", "1234", Set.of("WEAVING"),
				"admin@example.com", ownerId)).isInstanceOf(OrganizationValidationException.class);

		assertThat(sunatCalls).hasValue(0);
	}

	@Test
	void rejectsADuplicatedRuc() {
		var license = licenses.save(TestData.license(3, 10));
		companies.save(TestData.company(license, RUC));

		assertThatThrownBy(() -> useCase.execute("Textiles SAC", RUC, Set.of("WEAVING"),
				"admin@example.com", ownerId)).isInstanceOf(OrganizationConflictException.class)
				.hasMessageContaining("RUC");
	}

	@Test
	void blocksTheOperationWhenSunatIsUnavailable() {
		licenses.save(TestData.license(3, 10));
		sunatDown = true;

		assertThatThrownBy(() -> useCase.execute("Textiles SAC", RUC, Set.of("WEAVING"),
				"admin@example.com", ownerId)).isInstanceOf(ExternalServiceUnavailableException.class);

		assertThat(companies.findAll()).isEmpty();
	}

	@Test
	void requiresAtLeastOneActivityAndAValidAdminEmail() {
		licenses.save(TestData.license(3, 10));

		assertThatThrownBy(() -> useCase.execute("Textiles SAC", RUC, Set.of(),
				"admin@example.com", ownerId)).isInstanceOf(OrganizationValidationException.class);
		assertThatThrownBy(() -> useCase.execute("Textiles SAC", RUC, Set.of(" "),
				"admin@example.com", ownerId)).isInstanceOf(OrganizationValidationException.class);
		assertThatThrownBy(() -> useCase.execute("Textiles SAC", RUC, Set.of("WEAVING"),
				"not-an-email", ownerId)).isInstanceOf(OrganizationValidationException.class);
	}

	@Test
	void requiresAConfiguredLicense() {
		assertThatThrownBy(() -> useCase.execute("Textiles SAC", RUC, Set.of("WEAVING"),
				"admin@example.com", ownerId)).isInstanceOf(OrganizationNotFoundException.class);
	}
}
