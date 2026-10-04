package com.nodotextil.trazatex.organizationaccess.infrastructure;

import com.nodotextil.trazatex.organizationaccess.application.contract.OrganizationAccess;
import com.nodotextil.trazatex.organizationaccess.application.port.CompanyRepository;
import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.domain.Company;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Implements the {@link OrganizationAccess} contract on top of the module's ports. */
@Component
@Transactional(readOnly = true)
class OrganizationAccessAdapter implements OrganizationAccess {

	private final OrganizationUserRepository users;
	private final CompanyRepository companies;

	OrganizationAccessAdapter(OrganizationUserRepository users, CompanyRepository companies) {
		this.users = users;
		this.companies = companies;
	}

	@Override
	public List<UUID> activeUserIdsByCompanyAndRole(UUID companyId, String role) {
		if (companyId == null || role == null) {
			return List.of();
		}
		Role parsed;
		try {
			parsed = Role.valueOf(role);
		}
		catch (IllegalArgumentException unknownRole) {
			return List.of();
		}
		return users.findActiveByCompanyIdAndRole(companyId, parsed).stream()
				.map(OrganizationUser::id).toList();
	}

	@Override
	public List<UUID> activeUserIdsByCompany(UUID companyId) {
		if (companyId == null) {
			return List.of();
		}
		return users.findActiveByCompanyId(companyId).stream().map(OrganizationUser::id).toList();
	}

	@Override
	public boolean isCompanyActive(UUID companyId) {
		return companyId != null && companies.findById(companyId).map(Company::isActive)
				.orElse(false);
	}

	@Override
	public Optional<CompanyMembership> findMembership(UUID userId) {
		if (userId == null) {
			return Optional.empty();
		}
		return users.findById(userId).filter(OrganizationUser::isActive).map(user -> {
			boolean companyActive = user.companyId() != null && isCompanyActive(user.companyId());
			return new CompanyMembership(user.id(), user.companyId(), user.role().name(),
					companyActive);
		});
	}

	@Override
	public boolean companiesShareLicense(UUID firstCompanyId, UUID secondCompanyId) {
		if (firstCompanyId == null || secondCompanyId == null) {
			return false;
		}
		Optional<Company> first = companies.findById(firstCompanyId);
		Optional<Company> second = companies.findById(secondCompanyId);
		return first.isPresent() && second.isPresent()
				&& first.get().licenseId().equals(second.get().licenseId());
	}
}
