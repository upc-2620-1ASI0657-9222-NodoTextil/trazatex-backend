package com.nodotextil.trazatex.organizationaccess.application.usecase;

import com.nodotextil.trazatex.organizationaccess.application.port.out.InvitationLinkPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.InvitationRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.OrganizationEventPublisherPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.UserRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.domain.event.InvitationCreated;
import com.nodotextil.trazatex.organizationaccess.domain.model.Email;
import com.nodotextil.trazatex.organizationaccess.domain.model.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.model.InvitationToken;
import com.nodotextil.trazatex.organizationaccess.domain.model.License;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.domain.service.InvitationPolicy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Emisión de invitaciones, común a crear empresa, invitar operario, aprobar solicitud de administrador y al bootstrap.
 * Quien llama bloquea antes la fila de la empresa (o de la licencia) y ya está dentro de la transacción.
 */
@Component
public class InvitationIssuer {

    private final UserRepositoryPort users;
    private final InvitationRepositoryPort invitations;
    private final SlotCounter slots;
    private final OrganizationEventPublisherPort events;
    private final InvitationLinkPort links;

    InvitationIssuer(UserRepositoryPort users, InvitationRepositoryPort invitations, SlotCounter slots,
                     OrganizationEventPublisherPort events, InvitationLinkPort links) {
        this.users = users;
        this.invitations = invitations;
        this.slots = slots;
        this.events = events;
        this.links = links;
    }

    /** @param displayName nombre para mostrar tal como lo dio quien invita (no se divide); puede ser nulo */
    public Invitation issue(License license, Role role, UUID companyId, String email, String displayName, String firstName,
                            String lastName, String position, UUID issuedBy, Instant now) {
        Email address = new Email(email);
        Invitation invitation = Invitation.issue(UUID.randomUUID(), InvitationToken.generate(), address, displayName,
                firstName, lastName, position, role, companyId, issuedBy, now);
        long usedSlots = role.belongsToCompany() ? slots.usedSlots(companyId, now) : 0;
        InvitationPolicy.ensureCanInvite(license, role, usedSlots, users.existsByEmail(address),
                hasLivePendingInvitation(address, now));
        invitations.save(invitation);
        events.publish(InvitationCreated.of(invitation, links.acceptUrl(invitation.token().value())));
        return invitation;
    }

    /**
     * Una invitación vencida que el job aún no marcó seguiría ocupando el índice único de correos PENDING: se marca EXPIRED
     * (y se envía a la base) antes de insertar la nueva.
     */
    private boolean hasLivePendingInvitation(Email address, Instant now) {
        Optional<Invitation> stored = invitations.findStoredPendingByEmail(address);
        if (stored.isEmpty()) {
            return false;
        }
        if (stored.get().expire(now)) {
            invitations.save(stored.get());
            return false;
        }
        return true;
    }
}
