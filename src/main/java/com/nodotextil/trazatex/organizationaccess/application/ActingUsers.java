package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationAccessDeniedException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import java.util.UUID;

/** Resolves the user who performs an operation and checks their role and status. */
final class ActingUsers {

	private ActingUsers() {
	}

	/** The caller must be an active company administrator; they act on their own company. */
	static OrganizationUser activeCompanyAdmin(OrganizationUserRepository users, UUID userId) {
		return users.findById(userId)
				.filter(user -> user.isActive() && user.role() == Role.COMPANY_ADMIN)
				.orElseThrow(() -> new OrganizationAccessDeniedException(
						"Only an active company administrator can do this"));
	}
}
