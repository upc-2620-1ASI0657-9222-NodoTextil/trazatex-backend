package com.nodotextil.trazatex.organizationaccess.application.contract;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface OrganizationAccess {

	
	List<UUID> activeUserIdsByCompanyAndRole(UUID companyId, String role);

	
	List<UUID> activeUserIdsByCompany(UUID companyId);

	boolean isCompanyActive(UUID companyId);

	
	Optional<CompanyMembership> findMembership(UUID userId);

	
	boolean companiesShareLicense(UUID firstCompanyId, UUID secondCompanyId);

	


	record CompanyMembership(UUID userId, UUID companyId, String role, boolean companyActive) {
	}
}
