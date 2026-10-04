package com.nodotextil.trazatex.organizationaccess.infrastructure.config;

import com.nodotextil.trazatex.organizationaccess.application.ExpirePendingInvitationsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
class InvitationExpirationJob {

	private static final Logger log = LoggerFactory.getLogger(InvitationExpirationJob.class);

	private final ExpirePendingInvitationsUseCase expirePendingInvitations;

	InvitationExpirationJob(ExpirePendingInvitationsUseCase expirePendingInvitations) {
		this.expirePendingInvitations = expirePendingInvitations;
	}

	@Scheduled(fixedDelayString = "${app.invitations.expiration-interval-ms:300000}")
	void expireInvitations() {
		int expired = expirePendingInvitations.execute();
		if (expired > 0) {
			log.info("Expired {} pending invitation(s)", expired);
		}
	}
}
