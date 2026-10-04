package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.port.CompanyRepository;
import com.nodotextil.trazatex.organizationaccess.application.port.LicenseRepository;
import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.domain.Company;
import com.nodotextil.trazatex.organizationaccess.domain.License;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The license conditions and limits, the companies in use and the users of each (RF-001). */
@Service
public class GetLicenseOverviewUseCase {

	private final LicenseRepository licenses;
	private final CompanyRepository companies;
	private final OrganizationUserRepository users;

	public GetLicenseOverviewUseCase(LicenseRepository licenses, CompanyRepository companies,
			OrganizationUserRepository users) {
		this.licenses = licenses;
		this.companies = companies;
		this.users = users;
	}

	@Transactional(readOnly = true)
	public LicenseOverview execute() {
		License license = licenses.findFirst()
				.orElseThrow(() -> new OrganizationNotFoundException("License not configured"));
		List<CompanyUsage> usage = companies.findAll().stream()
				.map(company -> new CompanyUsage(company,
						users.countActiveByCompanyId(company.id())))
				.toList();
		return new LicenseOverview(license, companies.countByLicenseId(license.id()), usage);
	}

	public record LicenseOverview(License license, long usedCompanies,
			List<CompanyUsage> companies) {
	}

	public record CompanyUsage(Company company, long activeUsers) {
	}
}
