package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.port.AdminRequestRepository;
import com.nodotextil.trazatex.organizationaccess.domain.AdminRequest;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationNotFoundException;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The license owner approves or rejects an administrator request (RF-011). Approving needs a free
 * seat in the company; it then creates the administrator invitation with the first name, last name
 * and job title of the request. If there is no seat the request stays pending.
 */
@Service
public class DecideAdminRequestUseCase {

	private final AdminRequestRepository adminRequests;
	private final InvitationIssuer invitationIssuer;
	private final Clock clock;

	@Autowired
	public DecideAdminRequestUseCase(AdminRequestRepository adminRequests,
			InvitationIssuer invitationIssuer) {
		this(adminRequests, invitationIssuer, Clock.systemUTC());
	}

	DecideAdminRequestUseCase(AdminRequestRepository adminRequests,
			InvitationIssuer invitationIssuer, Clock clock) {
		this.adminRequests = adminRequests;
		this.invitationIssuer = invitationIssuer;
		this.clock = clock;
	}

	@Transactional
	public Decision execute(UUID requestId, boolean approve, UUID decidedByUserId) {
		AdminRequest request = adminRequests.findById(requestId)
				.orElseThrow(() -> new OrganizationNotFoundException("Request not found"));
		LocalDateTime now = LocalDateTime.now(clock);
		if (!approve) {
			return new Decision(adminRequests.save(request.reject(decidedByUserId, now)),
					Optional.empty());
		}
		AdminRequest approved = request.approve(decidedByUserId, now);
		Invitation invitation = invitationIssuer.issue(request.email(), request.companyId(),
				Role.COMPANY_ADMIN, request.firstName(), request.lastName(), request.jobTitle(),
				decidedByUserId);
		return new Decision(adminRequests.save(approved), Optional.of(invitation));
	}

	/** {@code invitation} is present only when the request was approved. */
	public record Decision(AdminRequest request, Optional<Invitation> invitation) {
	}
}
