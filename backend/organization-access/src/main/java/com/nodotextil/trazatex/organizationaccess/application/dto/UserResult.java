package com.nodotextil.trazatex.organizationaccess.application.dto;

import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.domain.model.User;
import com.nodotextil.trazatex.organizationaccess.domain.model.UserStatus;

import java.time.Instant;
import java.util.UUID;

/** Sin hash de contraseña ni contadores de login. */
public record UserResult(
        UUID id,
        UUID companyId,
        String email,
        String firstName,
        String lastName,
        String position,
        Role role,
        UserStatus status,
        Instant createdAt) {

    public static UserResult from(User user) {
        return new UserResult(user.id(), user.companyId(), user.email().value(), user.firstName(), user.lastName(),
                user.position(), user.role(), user.status(), user.createdAt());
    }
}
