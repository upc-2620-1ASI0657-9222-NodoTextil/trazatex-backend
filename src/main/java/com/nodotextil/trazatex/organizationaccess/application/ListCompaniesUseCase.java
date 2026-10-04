package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.port.CompanyRepository;
import com.nodotextil.trazatex.organizationaccess.domain.Company;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lists the companies of the chain, active and inactive (RF-003). */
@Service
public class ListCompaniesUseCase {

	private final CompanyRepository companies;

	public ListCompaniesUseCase(CompanyRepository companies) {
		this.companies = companies;
	}

	@Transactional(readOnly = true)
	public List<Company> execute() {
		return companies.findAll();
	}
}
