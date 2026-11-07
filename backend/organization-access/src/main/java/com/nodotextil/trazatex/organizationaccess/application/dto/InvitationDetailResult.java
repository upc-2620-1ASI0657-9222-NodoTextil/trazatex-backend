package com.nodotextil.trazatex.organizationaccess.application.dto;

import com.nodotextil.trazatex.organizationaccess.domain.model.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.model.InvitationStatus;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;

import java.time.Instant;

/** Lo que ve quien abre el enlace (endpoint público): lo necesario para el formulario de aceptación. */
public record InvitationDetailResult(
        String email,
        String displayName,
        String firstName,
        String lastName,
        Role role,
        String companyName,
        InvitationStatus status,
        Instant expiresAt) {

    public static InvitationDetailResult from(Invitation invitation, String companyName, Instant now) {
        return new InvitationDetailResult(invitation.email().value(), invitation.displayName(), invitation.firstName(), invitation.lastName(),
                invitation.role(), companyName, invitation.effectiveStatus(now), invitation.expiresAt());
    }
}
