package com.nodotextil.trazatex.organizationaccess.application.usecase;

import com.nodotextil.trazatex.organizationaccess.application.port.out.InvitationRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.UserRepositoryPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
class SlotCounter {

    private final UserRepositoryPort users;
    private final InvitationRepositoryPort invitations;

    SlotCounter(UserRepositoryPort users, InvitationRepositoryPort invitations) {
        this.users = users;
        this.invitations = invitations;
    }

    long usedSlots(UUID companyId, Instant now) {
        return users.countActiveByCompany(companyId) + invitations.countOccupyingSlots(companyId, now);
    }
}
