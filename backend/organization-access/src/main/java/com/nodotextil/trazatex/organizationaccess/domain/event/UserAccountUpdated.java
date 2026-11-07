package com.nodotextil.trazatex.organizationaccess.domain.event;

import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.domain.model.User;
import com.nodotextil.trazatex.organizationaccess.domain.model.UserStatus;

import java.util.UUID;

/** Tópico {@code orgaccess.user}, clave userId. Lo consume notification. Al aceptar la invitación y al cambiar el estado. */
public record UserAccountUpdated(
        UUID userId,
        String email,
        String fullName,
        Role role,
        UUID companyId,
        UserStatus status) implements OrganizationEvent {

    public static final String TYPE = "UserAccountUpdated";

    public static UserAccountUpdated of(User user) {
        return new UserAccountUpdated(user.id(), user.email().value(), user.fullName(), user.role(),
                user.companyId(), user.status());
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String aggregateId() {
        return userId.toString();
    }
}
