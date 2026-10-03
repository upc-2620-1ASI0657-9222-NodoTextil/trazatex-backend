package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.port.AdminRequestRepository;
import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.domain.AdminRequest;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationConflictException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A company administrator asks the license owner to add another administrator (RF-010). First
 * name, last name, email and job title are mandatory, and a company cannot have two pending
 * requests for the same email.
 */
@Service
public class CreateAdminRequestUseCase {

	private final OrganizationUserRepository users;
	private final AdminRequestRepository adminRequests;
	private final Clock clock;

	@Autowired
	public CreateAdminRequestUseCase(OrganizationUserRepository users,
			AdminRequestRepository adminRequests) {
		this(users, adminRequests, Clock.systemUTC());
	}

	CreateAdminRequestUseCase(OrganizationUserRepository users,
			AdminRequestRepository adminRequests, Clock clock) {
		this.users = users;
		this.adminRequests = adminRequests;
		this.clock = clock;
	}

	@Transactional
	public AdminRequest execute(UUID adminUserId, String firstName, String lastName, String email,
			String jobTitle) {
		OrganizationUser admin = ActingUsers.activeCompanyAdmin(users, adminUserId);
		RequiredFields.require("First name, last name, email and job title are required",
				Map.of("firstName", nullToEmpty(firstName), "lastName", nullToEmpty(lastName),
						"email", nullToEmpty(email), "jobTitle", nullToEmpty(jobTitle)));
		AdminRequest request = AdminRequest.create(admin.companyId(), email, firstName,
				lastName, jobTitle, admin.id(), LocalDateTime.now(clock));
		if (users.findByEmail(request.email()).isPresent()) {
			throw new OrganizationConflictException("A user with this email already exists");
		}
		if (adminRequests.existsPendingByCompanyIdAndEmail(request.companyId(),
				request.email())) {
			throw new OrganizationConflictException(
					"There is already a pending request for this email in your company");
		}
		return adminRequests.save(request);
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
