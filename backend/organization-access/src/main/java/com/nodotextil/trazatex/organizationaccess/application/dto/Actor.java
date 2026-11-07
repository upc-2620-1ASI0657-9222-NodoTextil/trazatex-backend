package com.nodotextil.trazatex.organizationaccess.application.dto;

import com.nodotextil.trazatex.organizationaccess.domain.model.Role;

import java.util.UUID;

/** Quien hace la petición, según las cabeceras que inyecta el Gateway (ADR-005). {@code companyId} es nulo para el LICENSE_OWNER. */
public record Actor(UUID userId, UUID companyId, Role role) {
}
