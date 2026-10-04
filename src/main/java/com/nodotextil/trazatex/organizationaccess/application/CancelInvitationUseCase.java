package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.port.InvitationRepository;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationAccessDeniedException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationConflictException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationNotFoundException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cancels an invitation (RF-005): only its issuer, and only while it is still pending. */
@Service
public class CancelInvitationUseCase {

	private final InvitationRepository invitations;
	private final Clock clock;

	@Autowired
	public CancelInvitationUseCase(InvitationRepository invitations) {
		this(invitations, Clock.systemUTC());
	}

	CancelInvitationUseCase(InvitationRepository invitations, Clock clock) {
		this.invitations = invitations;
		this.clock = clock;
	}

	@Transactional
	public Invitation execute(UUID invitationId, UUID requestedByUserId) {
		Invitation invitation = invitations.findById(invitationId)
				.orElseThrow(() -> new OrganizationNotFoundException("Invitation not found"));
		if (!invitation.createdByUserId().equals(requestedByUserId)) {
			throw new OrganizationAccessDeniedException(
					"Only the user who issued the invitation can cancel it");
		}
		if (!invitation.isUsableAt(LocalDateTime.now(clock))) {
			throw new OrganizationConflictException("Only a pending invitation can be cancelled");
		}
		return invitations.save(invitation.cancel());
	}
}
