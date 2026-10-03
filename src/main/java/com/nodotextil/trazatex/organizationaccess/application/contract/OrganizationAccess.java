package com.nodotextil.trazatex.organizationaccess.application.contract;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contract published by the Organization &amp; Access module to the rest of the monolith.
 * Other modules must use this interface instead of reaching into the module's persistence
 * or REST API.
 */
public interface OrganizationAccess {

	/** Active user ids of a company that have the given role (a {@code Role} name). */
	List<UUID> activeUserIdsByCompanyAndRole(UUID companyId, String role);

	/** Active user ids of a company, regardless of role. */
	List<UUID> activeUserIdsByCompany(UUID companyId);

	boolean isCompanyActive(UUID companyId);

	Optional<CompanyMembership> findMembership(UUID userId);

	/** Whether both companies belong to the same license. */
	boolean companiesShareLicense(UUID firstCompanyId, UUID secondCompanyId);

	/**
	 * Snapshot of a user's membership. {@code companyId} is {@code null} for a
	 * {@code LICENSE_OWNER}; {@code role} is the name of the user's role.
	 */
	record CompanyMembership(UUID userId, UUID companyId, String role, boolean companyActive) {
	}
}
