package com.nodotextil.trazatex.organizationaccess.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nodotextil.trazatex.organizationaccess.application.contract.OrganizationAccess.CompanyMembership;
import com.nodotextil.trazatex.organizationaccess.application.port.CompanyRepository;
import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.domain.Company;
import com.nodotextil.trazatex.organizationaccess.domain.CompanyStatus;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.domain.UserStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OrganizationAccessAdapterTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 9, 0);

	private final OrganizationUserRepository users = mock(OrganizationUserRepository.class);
	private final CompanyRepository companies = mock(CompanyRepository.class);
	private final OrganizationAccessAdapter adapter = new OrganizationAccessAdapter(users,
			companies);

	private final UUID companyId = UUID.randomUUID();
	private final UUID licenseId = UUID.randomUUID();

	private Company company(UUID id, UUID license, CompanyStatus status) {
		return new Company(id, license, "20123456789", "Textiles SAC", Set.of("WEAVING"), status,
				NOW);
	}

	private OrganizationUser user(Role role, UUID company, UserStatus status) {
		return new OrganizationUser(UUID.randomUUID(), "u@example.com", "Ana", "Lopez", null,
				"hash", role, company, status, 0, null, NOW);
	}

	@Test
	void listsActiveUserIdsOfACompanyAndRole() {
		OrganizationUser admin = user(Role.COMPANY_ADMIN, companyId, UserStatus.ACTIVE);
		when(users.findActiveByCompanyIdAndRole(companyId, Role.COMPANY_ADMIN))
				.thenReturn(List.of(admin));

		assertThat(adapter.activeUserIdsByCompanyAndRole(companyId, "COMPANY_ADMIN"))
				.containsExactly(admin.id());
	}

	@Test
	void unknownRoleYieldsNoUsers() {
		assertThat(adapter.activeUserIdsByCompanyAndRole(companyId, "WIZARD")).isEmpty();
		verifyNoInteractions(users);
	}

	@Test
	void listsActiveUserIdsOfACompany() {
		OrganizationUser operator = user(Role.OPERATOR, companyId, UserStatus.ACTIVE);
		when(users.findActiveByCompanyId(companyId)).thenReturn(List.of(operator));

		assertThat(adapter.activeUserIdsByCompany(companyId)).containsExactly(operator.id());
	}

	@Test
	void companyIsActiveOnlyWhenItExistsAndIsActive() {
		UUID inactiveId = UUID.randomUUID();
		when(companies.findById(any(UUID.class))).thenAnswer(invocation -> {
			UUID id = invocation.getArgument(0);
			if (id.equals(companyId)) {
				return Optional.of(company(companyId, licenseId, CompanyStatus.ACTIVE));
			}
			if (id.equals(inactiveId)) {
				return Optional.of(company(inactiveId, licenseId, CompanyStatus.INACTIVE));
			}
			return Optional.empty();
		});

		assertThat(adapter.isCompanyActive(companyId)).isTrue();
		assertThat(adapter.isCompanyActive(inactiveId)).isFalse();
		assertThat(adapter.isCompanyActive(UUID.randomUUID())).isFalse();
		assertThat(adapter.isCompanyActive(null)).isFalse();
	}

	@Test
	void membershipOfAnActiveCompanyUser() {
		OrganizationUser operator = user(Role.OPERATOR, companyId, UserStatus.ACTIVE);
		when(users.findById(operator.id())).thenReturn(Optional.of(operator));
		when(companies.findById(companyId))
				.thenReturn(Optional.of(company(companyId, licenseId, CompanyStatus.ACTIVE)));

		assertThat(adapter.findMembership(operator.id())).contains(
				new CompanyMembership(operator.id(), companyId, "OPERATOR", true));
	}

	@Test
	void membershipReportsAnInactiveCompany() {
		OrganizationUser admin = user(Role.COMPANY_ADMIN, companyId, UserStatus.ACTIVE);
		when(users.findById(admin.id())).thenReturn(Optional.of(admin));
		when(companies.findById(companyId))
				.thenReturn(Optional.of(company(companyId, licenseId, CompanyStatus.INACTIVE)));

		assertThat(adapter.findMembership(admin.id()).orElseThrow().companyActive()).isFalse();
	}

	@Test
	void licenseOwnerMembershipHasNoCompany() {
		OrganizationUser owner = user(Role.LICENSE_OWNER, null, UserStatus.ACTIVE);
		when(users.findById(owner.id())).thenReturn(Optional.of(owner));

		CompanyMembership membership = adapter.findMembership(owner.id()).orElseThrow();

		assertThat(membership.companyId()).isNull();
		assertThat(membership.role()).isEqualTo("LICENSE_OWNER");
		assertThat(membership.companyActive()).isFalse();
		verifyNoInteractions(companies);
	}

	@Test
	void noMembershipForInactiveOrPendingOrUnknownUsers() {
		OrganizationUser inactive = user(Role.OPERATOR, companyId, UserStatus.INACTIVE);
		OrganizationUser pending = user(Role.OPERATOR, companyId, UserStatus.PENDING);
		when(users.findById(inactive.id())).thenReturn(Optional.of(inactive));
		when(users.findById(pending.id())).thenReturn(Optional.of(pending));
		UUID unknown = UUID.randomUUID();
		when(users.findById(unknown)).thenReturn(Optional.empty());

		assertThat(adapter.findMembership(inactive.id())).isEmpty();
		assertThat(adapter.findMembership(pending.id())).isEmpty();
		assertThat(adapter.findMembership(unknown)).isEmpty();
		assertThat(adapter.findMembership(null)).isEmpty();
	}

	@Test
	void companiesShareLicenseOnlyWhenBothExistAndHaveTheSameLicense() {
		UUID secondId = UUID.randomUUID();
		UUID otherLicenseCompanyId = UUID.randomUUID();
		when(companies.findById(companyId))
				.thenReturn(Optional.of(company(companyId, licenseId, CompanyStatus.ACTIVE)));
		when(companies.findById(secondId))
				.thenReturn(Optional.of(company(secondId, licenseId, CompanyStatus.INACTIVE)));
		when(companies.findById(otherLicenseCompanyId)).thenReturn(Optional
				.of(company(otherLicenseCompanyId, UUID.randomUUID(), CompanyStatus.ACTIVE)));
		UUID unknown = UUID.randomUUID();
		when(companies.findById(unknown)).thenReturn(Optional.empty());

		assertThat(adapter.companiesShareLicense(companyId, secondId)).isTrue();
		assertThat(adapter.companiesShareLicense(companyId, otherLicenseCompanyId)).isFalse();
		assertThat(adapter.companiesShareLicense(companyId, unknown)).isFalse();
		assertThat(adapter.companiesShareLicense(null, companyId)).isFalse();
	}
}
