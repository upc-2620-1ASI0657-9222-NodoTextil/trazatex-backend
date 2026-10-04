package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationAccessDeniedException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationConflictException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationNotFoundException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.domain.UserStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A company administrator activates or inactivates a user of their own company (RF-012). The
 * license owner cannot be touched and a user never goes back to {@code PENDING}. Reactivating a
 * user needs a free seat (RF-013); inactivating needs nothing.
 */
@Service
public class ChangeUserStatusUseCase {

	private final OrganizationUserRepository users;
	private final InvitationIssuer invitationIssuer;

	public ChangeUserStatusUseCase(OrganizationUserRepository users,
			InvitationIssuer invitationIssuer) {
		this.users = users;
		this.invitationIssuer = invitationIssuer;
	}

	@Transactional
	public OrganizationUser execute(UUID adminUserId, UUID targetUserId, UserStatus newStatus) {
		OrganizationUser admin = ActingUsers.activeCompanyAdmin(users, adminUserId);
		OrganizationUser target = users.findById(targetUserId)
				.orElseThrow(() -> new OrganizationNotFoundException("User not found"));
		if (target.role() == Role.LICENSE_OWNER
				|| !admin.companyId().equals(target.companyId())) {
			throw new OrganizationAccessDeniedException(
					"You can only manage the users of your own company");
		}
		OrganizationUser updated = target.withStatus(newStatus);
		if (updated.status() == target.status()) {
			return target;
		}
		if (updated.status() == UserStatus.ACTIVE
				&& !invitationIssuer.hasSeatAvailable(target.companyId())) {
			throw new OrganizationConflictException(
					"The company has reached its user limit of the license");
		}
		return users.save(updated);
	}
}
