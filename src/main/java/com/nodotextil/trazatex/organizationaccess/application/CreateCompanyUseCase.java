package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.port.CompanyRepository;
import com.nodotextil.trazatex.organizationaccess.application.port.LicenseRepository;
import com.nodotextil.trazatex.organizationaccess.application.port.TaxpayerValidationPort;
import com.nodotextil.trazatex.organizationaccess.domain.Company;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.License;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationConflictException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationNotFoundException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class CreateCompanyUseCase {

	private final LicenseRepository licenses;
	private final CompanyRepository companies;
	private final TaxpayerValidationPort taxpayerValidation;
	private final InvitationIssuer invitationIssuer;
	private final Clock clock;

	@Autowired
	public CreateCompanyUseCase(LicenseRepository licenses, CompanyRepository companies,
			TaxpayerValidationPort taxpayerValidation, InvitationIssuer invitationIssuer) {
		this(licenses, companies, taxpayerValidation, invitationIssuer, Clock.systemUTC());
	}

	CreateCompanyUseCase(LicenseRepository licenses, CompanyRepository companies,
			TaxpayerValidationPort taxpayerValidation, InvitationIssuer invitationIssuer,
			Clock clock) {
		this.licenses = licenses;
		this.companies = companies;
		this.taxpayerValidation = taxpayerValidation;
		this.invitationIssuer = invitationIssuer;
		this.clock = clock;
	}

	@Transactional
	public CreatedCompany execute(String legalName, String ruc, Set<String> activities,
			String adminEmail, UUID requestedByUserId) {
		License license = licenses.findFirst()
				.orElseThrow(() -> new OrganizationNotFoundException("License not configured"));
		Company company = Company.create(license.id(), ruc, legalName,
				cleanActivities(activities), LocalDateTime.now(clock));

		if (!license.allowsAnotherCompany(companies.countByLicenseId(license.id()))) {
			throw new OrganizationConflictException(
					"The license has reached its limit of " + license.maxCompanies() + " companies");
		}
		if (companies.existsByRuc(company.ruc())) {
			throw new OrganizationConflictException("A company with this RUC already exists");
		}
		if (!taxpayerValidation.isActiveAndHabido(company.ruc())) {
			throw new OrganizationValidationException("The RUC is not valid in SUNAT",
					Map.of("ruc", "Must belong to an active and habido taxpayer"));
		}

		Company saved = companies.save(company);
		Invitation invitation = invitationIssuer.issue(adminEmail, saved.id(),
				Role.COMPANY_ADMIN, null, null, null, requestedByUserId);
		return new CreatedCompany(saved, invitation);
	}

	private static Set<String> cleanActivities(Set<String> activities) {
		Set<String> cleaned = activities == null ? Set.of() : activities.stream()
				.filter(activity -> activity != null && !activity.isBlank())
				.map(String::trim).collect(java.util.stream.Collectors.toUnmodifiableSet());
		if (cleaned.isEmpty()) {
			throw new OrganizationValidationException("At least one textile activity is required",
					Map.of("activities", "At least one activity is required"));
		}
		return cleaned;
	}

	public record CreatedCompany(Company company, Invitation adminInvitation) {
	}
}
