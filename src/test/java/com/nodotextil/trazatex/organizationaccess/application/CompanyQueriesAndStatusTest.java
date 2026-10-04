package com.nodotextil.trazatex.organizationaccess.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodotextil.trazatex.organizationaccess.application.GetLicenseOverviewUseCase.LicenseOverview;
import com.nodotextil.trazatex.organizationaccess.domain.Company;
import com.nodotextil.trazatex.organizationaccess.domain.CompanyStatus;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationNotFoundException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryCompanyRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryLicenseRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryOrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.support.TestData;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CompanyQueriesAndStatusTest {

	private final InMemoryLicenseRepository licenses = new InMemoryLicenseRepository();
	private final InMemoryCompanyRepository companies = new InMemoryCompanyRepository();
	private final InMemoryOrganizationUserRepository users = new InMemoryOrganizationUserRepository();

	@Test
	void inactivatingAndReactivatingKeepsEverythingElse() {
		var license = licenses.save(TestData.license(3, 10));
		Company company = companies.save(TestData.company(license, "20123456789"));
		var useCase = new ChangeCompanyStatusUseCase(companies);

		Company inactive = useCase.execute(company.id(), CompanyStatus.INACTIVE);

		assertThat(inactive.status()).isEqualTo(CompanyStatus.INACTIVE);
		assertThat(inactive.ruc()).isEqualTo(company.ruc());
		assertThat(inactive.legalName()).isEqualTo(company.legalName());
		assertThat(inactive.createdAt()).isEqualTo(company.createdAt());
		assertThat(companies.findById(company.id()).orElseThrow().isActive()).isFalse();

		Company active = useCase.execute(company.id(), CompanyStatus.ACTIVE);
		assertThat(active.isActive()).isTrue();
	}

	@Test
	void sameStatusIsANoOp() {
		var license = licenses.save(TestData.license(3, 10));
		Company company = companies.save(TestData.company(license, "20123456789"));

		assertThat(new ChangeCompanyStatusUseCase(companies)
				.execute(company.id(), CompanyStatus.ACTIVE)).isEqualTo(company);
	}

	@Test
	void unknownCompanyAndMissingStatusAreRejected() {
		var useCase = new ChangeCompanyStatusUseCase(companies);

		assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), CompanyStatus.ACTIVE))
				.isInstanceOf(OrganizationNotFoundException.class);
		assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), null))
				.isInstanceOf(OrganizationValidationException.class);
	}

	@Test
	void listsEveryCompanyIncludingInactiveOnes() {
		var license = licenses.save(TestData.license(3, 10));
		Company first = companies.save(TestData.company(license, "20111111111"));
		Company second = companies.save(TestData.company(license, "20222222222")
				.withStatus(CompanyStatus.INACTIVE));

		assertThat(new ListCompaniesUseCase(companies).execute()).containsExactly(first, second);
	}

	@Test
	void licenseOverviewShowsLimitsUsedCompaniesAndUsersPerCompany() {
		var license = licenses.save(TestData.license(3, 10));
		Company first = companies.save(TestData.company(license, "20111111111"));
		Company second = companies.save(TestData.company(license, "20222222222"));
		users.save(TestData.user("a@example.com", Role.COMPANY_ADMIN, first.id()));
		users.save(TestData.user("b@example.com", Role.OPERATOR, first.id()));
		users.save(TestData.user("c@example.com", Role.OPERATOR, second.id()));

		LicenseOverview overview = new GetLicenseOverviewUseCase(licenses, companies, users)
				.execute();

		assertThat(overview.license().maxCompanies()).isEqualTo(3);
		assertThat(overview.license().maxUsersPerCompany()).isEqualTo(10);
		assertThat(overview.usedCompanies()).isEqualTo(2);
		assertThat(overview.companies()).extracting(usage -> usage.company().id(),
				usage -> usage.activeUsers()).containsExactly(
						org.assertj.core.groups.Tuple.tuple(first.id(), 2L),
						org.assertj.core.groups.Tuple.tuple(second.id(), 1L));
	}

	@Test
	void licenseOverviewNeedsALicense() {
		assertThatThrownBy(() -> new GetLicenseOverviewUseCase(licenses, companies, users).execute())
				.isInstanceOf(OrganizationNotFoundException.class);
	}
}
