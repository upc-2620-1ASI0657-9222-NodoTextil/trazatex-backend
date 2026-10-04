package com.nodotextil.trazatex.shared.security;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

public record AuthenticatedUser(UUID userId, UUID companyId, String role) {

    public static AuthenticatedUser from(Jwt jwt) {
        String userId = jwt.getClaimAsString("userId");
        String companyId = jwt.getClaimAsString("companyId");
        String role = jwt.getClaimAsString("role");
        if (userId == null || role == null) {
            throw new AccessDeniedException("Invalid authentication context");
        }
        return new AuthenticatedUser(
                UUID.fromString(userId),
                companyId == null ? null : UUID.fromString(companyId),
                role);
    }

    public UUID requireCompanyId() {
        if (companyId == null) {
            throw new AccessDeniedException("Company context is required");
        }
        return companyId;
    }
}
