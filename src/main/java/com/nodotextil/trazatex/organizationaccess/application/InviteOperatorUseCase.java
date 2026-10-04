package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
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
		OrganizationUser admin = ActingUsers.activeCompanyAdmin(users, adminUserId);
		RequiredFields.require("First name, last name, email and job title are required",
				Map.of("firstName", nullToEmpty(firstName), "lastName", nullToEmpty(lastName),
						"email", nullToEmpty(email), "jobTitle", nullToEmpty(jobTitle)));
		return invitationIssuer.issue(email, admin.companyId(), Role.OPERATOR, firstName,
				lastName, jobTitle, admin.id());
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
