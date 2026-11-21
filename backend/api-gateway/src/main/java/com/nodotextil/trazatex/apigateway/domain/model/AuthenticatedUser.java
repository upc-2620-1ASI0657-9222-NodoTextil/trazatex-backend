package com.nodotextil.trazatex.apigateway.domain.model;

import java.util.Objects;
import java.util.UUID;

public record AuthenticatedUser(UUID userId, Role role, UUID companyId) {

    public AuthenticatedUser {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
        if (role == Role.LICENSE_OWNER && companyId != null) {
            throw new IllegalArgumentException("El Dueño de Licencia no pertenece a una empresa");
        }
        if (role != Role.LICENSE_OWNER && companyId == null) {
            throw new IllegalArgumentException("El rol " + role + " requiere empresa");
        }
    }
}
