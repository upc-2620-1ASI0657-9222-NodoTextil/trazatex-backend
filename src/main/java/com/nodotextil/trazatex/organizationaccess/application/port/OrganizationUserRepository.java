package com.nodotextil.trazatex.organizationaccess.application.port;

import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationUserRepository {

	OrganizationUser save(OrganizationUser user);

	Optional<OrganizationUser> findById(UUID id);

	/** Case-insensitive: emails are stored normalized to lower case. */
	Optional<OrganizationUser> findByEmail(String email);

	List<OrganizationUser> findByCompanyId(UUID companyId);

	List<OrganizationUser> findActiveByCompanyId(UUID companyId);

	List<OrganizationUser> findActiveByCompanyIdAndRole(UUID companyId, Role role);

	long countActiveByCompanyId(UUID companyId);
}
