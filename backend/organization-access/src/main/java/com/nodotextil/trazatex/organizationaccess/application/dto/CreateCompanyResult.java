package com.nodotextil.trazatex.organizationaccess.application.dto;

import java.util.UUID;

/** La empresa creada y la invitación de su primer administrador (sin el token: solo viaja en el enlace del correo). */
public record CreateCompanyResult(CompanyResult company, UUID adminInvitationId) {
}
