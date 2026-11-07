package com.nodotextil.trazatex.organizationaccess.application.usecase;

import com.nodotextil.trazatex.organizationaccess.application.port.out.InvitationRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.UserRepositoryPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Cupo usado de una empresa (RF-013) = usuarios ACTIVE + invitaciones PENDING <b>vigentes</b> ({@code expires_at > now}): una
 * invitación vencida no ocupa cupo aunque el job de expiración aún no la haya marcado. Llamarlo con la fila de la empresa
 * bloqueada ({@code CompanyRepositoryPort.findByIdForUpdate}) para que el conteo y el alta no se crucen con otra operación.
 */
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
