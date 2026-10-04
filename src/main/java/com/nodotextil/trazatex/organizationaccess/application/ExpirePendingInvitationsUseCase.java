package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.port.InvitationRepository;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves every pending invitation that has reached its seven-day expiry to expired (RF-005),
 * which also frees the seat it held (RF-013). Accepted and cancelled invitations are never
 * touched, and running it again finds nothing left to do.
 */
@Service
public class ExpirePendingInvitationsUseCase {

	private final InvitationRepository invitations;
	private final Clock clock;

	@Autowired
	public ExpirePendingInvitationsUseCase(InvitationRepository invitations) {
		this(invitations, Clock.systemUTC());
	}

	ExpirePendingInvitationsUseCase(InvitationRepository invitations, Clock clock) {
		this.invitations = invitations;
		this.clock = clock;
	}

	/** @return how many invitations were expired by this run */
	@Transactional
	public int execute() {
		List<Invitation> due = invitations
				.findPendingExpiringAtOrBefore(LocalDateTime.now(clock));
		due.forEach(invitation -> invitations.save(invitation.expire()));
		return due.size();
	}
}
