package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.port.CompanyRepository;
import com.nodotextil.trazatex.organizationaccess.domain.Company;
import com.nodotextil.trazatex.organizationaccess.domain.CompanyStatus;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationNotFoundException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class ChangeCompanyStatusUseCase {

	private final CompanyRepository companies;

	public ChangeCompanyStatusUseCase(CompanyRepository companies) {
		this.companies = companies;
	}

	@Transactional
	public Company execute(UUID companyId, CompanyStatus status) {
		if (status == null) {
			throw new OrganizationValidationException("status is required");
		}
		Company company = companies.findById(companyId)
				.orElseThrow(() -> new OrganizationNotFoundException("Company not found"));
		if (company.status() == status) {
			return company;
		}
		return companies.save(company.withStatus(status));
	}
}
