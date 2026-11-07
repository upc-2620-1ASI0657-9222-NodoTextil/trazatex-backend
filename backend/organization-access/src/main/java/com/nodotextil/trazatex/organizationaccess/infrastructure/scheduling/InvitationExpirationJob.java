package com.nodotextil.trazatex.organizationaccess.infrastructure.scheduling;

import com.nodotextil.trazatex.organizationaccess.application.port.in.ManageInvitationsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Marca EXPIRED las invitaciones PENDING vencidas (organization-access.md). La verificación al leer o aceptar no depende de este job. */
@Component
class InvitationExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(InvitationExpirationJob.class);

    private final ManageInvitationsUseCase invitations;

    InvitationExpirationJob(ManageInvitationsUseCase invitations) {
        this.invitations = invitations;
    }

    @Scheduled(fixedDelayString = "${trazatex.invitations.expiration-job-delay}")
    void expireDueInvitations() {
        int expired = invitations.expireDueInvitations();
        if (expired > 0) {
            log.info("Invitaciones marcadas EXPIRED: {}", expired);
        }
    }
}
