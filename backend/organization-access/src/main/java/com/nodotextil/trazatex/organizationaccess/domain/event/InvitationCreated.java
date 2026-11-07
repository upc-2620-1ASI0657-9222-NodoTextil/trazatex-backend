package com.nodotextil.trazatex.organizationaccess.domain.event;

import com.nodotextil.trazatex.organizationaccess.domain.model.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;

import java.time.Instant;
import java.util.UUID;

/**
 * Tópico {@code orgaccess.invitation}, clave invitationId. Lo consume notification. {@code fullName} es nulo si la
 * invitación no trae nombres (los completa el invitado al aceptar); {@code companyId} es nulo para el Dueño.
 */
public record InvitationCreated(
        UUID invitationId,
        String email,
        String fullName,
        Role role,
        UUID companyId,
        String acceptUrl,
        Instant expiresAt) implements OrganizationEvent {

    public static final String TYPE = "InvitationCreated";

    public static InvitationCreated of(Invitation invitation, String acceptUrl) {
        return new InvitationCreated(invitation.id(), invitation.email().value(), invitation.fullName(),
                invitation.role(), invitation.companyId(), acceptUrl, invitation.expiresAt());
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String aggregateId() {
        return invitationId.toString();
    }
}
