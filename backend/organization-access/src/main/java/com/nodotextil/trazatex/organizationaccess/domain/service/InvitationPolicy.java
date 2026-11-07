package com.nodotextil.trazatex.organizationaccess.domain.service;

import com.nodotextil.trazatex.organizationaccess.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.organizationaccess.domain.model.License;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;

/**
 * Reglas comunes a toda emisión de invitación: crear empresa (primer administrador), invitar operario y aprobar una
 * solicitud de administrador. Los datos los consulta la capa de aplicación con la fila de la empresa bloqueada.
 */
public final class InvitationPolicy {

    private InvitationPolicy() {
    }

    /**
     * @param usedSlots usuarios ACTIVE + invitaciones PENDING vigentes de la empresa (no aplica al LICENSE_OWNER, que no ocupa cupo)
     */
    public static void ensureCanInvite(License license, Role role, long usedSlots, boolean emailHasAccount,
                                       boolean emailHasPendingInvitation) {
        if (emailHasAccount) {
            throw new ConflictException(ErrorCodes.EMAIL_ALREADY_REGISTERED, "Ese correo ya tiene una cuenta.");
        }
        if (emailHasPendingInvitation) {
            throw new ConflictException(ErrorCodes.INVITATION_ALREADY_PENDING, "Ese correo ya tiene una invitación pendiente.");
        }
        if (role.belongsToCompany()) {
            license.ensureUserCapacity(usedSlots);
        }
    }
}
