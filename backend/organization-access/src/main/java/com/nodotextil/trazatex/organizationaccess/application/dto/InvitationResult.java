package com.nodotextil.trazatex.organizationaccess.application.dto;

import com.nodotextil.trazatex.organizationaccess.domain.model.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.model.InvitationStatus;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;

import java.time.Instant;
import java.util.UUID;

/** Nunca incluye el token. {@code status} es el estado efectivo: una PENDING vencida se informa EXPIRED. */
public record InvitationResult(
        UUID id,
        String email,
        String displayName,
        String firstName,
        String lastName,
        String position,
        Role role,
        UUID companyId,
        UUID issuedBy,
        InvitationStatus status,
        Instant createdAt,
        Instant expiresAt) {

    public static InvitationResult from(Invitation invitation, Instant now) {
        return new InvitationResult(invitation.id(), invitation.email().value(), invitation.displayName(), invitation.firstName(),
                invitation.lastName(), invitation.position(), invitation.role(), invitation.companyId(),
                invitation.issuedBy(), invitation.effectiveStatus(now), invitation.createdAt(), invitation.expiresAt());
    }
}
