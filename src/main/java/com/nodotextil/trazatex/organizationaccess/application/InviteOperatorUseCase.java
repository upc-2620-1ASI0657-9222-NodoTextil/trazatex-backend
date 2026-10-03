package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationAccessDeniedException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A company administrator invites an operator to their own company (RF-009). First name, last
 * name, email and job title are mandatory. It fails when the company has no seat left (RF-013).
 */
@Service
public class InviteOperatorUseCase {

	private final OrganizationUserRepository users;
	private final InvitationIssuer invitationIssuer;

	public InviteOperatorUseCase(OrganizationUserRepository users,
			InvitationIssuer invitationIssuer) {
		this.users = users;
		this.invitationIssuer = invitationIssuer;
	}

	@Transactional
	public Invitation execute(UUID adminUserId, String firstName, String lastName, String email,
			String jobTitle) {
		OrganizationUser admin = users.findById(adminUserId)
				.filter(user -> user.isActive() && user.role() == Role.COMPANY_ADMIN)
				.orElseThrow(() -> new OrganizationAccessDeniedException(
						"Only an active company administrator can invite operators"));
		requireFields(firstName, lastName, email, jobTitle);
		return invitationIssuer.issue(email, admin.companyId(), Role.OPERATOR, firstName,
				lastName, jobTitle, admin.id());
	}

	private static void requireFields(String firstName, String lastName, String email,
			String jobTitle) {
		Map<String, String> missing = new LinkedHashMap<>();
		requireText(missing, "firstName", firstName);
		requireText(missing, "lastName", lastName);
		requireText(missing, "email", email);
		requireText(missing, "jobTitle", jobTitle);
		if (!missing.isEmpty()) {
			throw new OrganizationValidationException(
					"First name, last name, email and job title are required", missing);
		}
	}

	private static void requireText(Map<String, String> missing, String field, String value) {
		if (value == null || value.isBlank()) {
			missing.put(field, "is required");
		}
	}
}
