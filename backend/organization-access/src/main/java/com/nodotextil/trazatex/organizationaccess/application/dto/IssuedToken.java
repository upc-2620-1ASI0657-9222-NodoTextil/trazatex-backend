package com.nodotextil.trazatex.organizationaccess.application.dto;

import java.time.Instant;

public record IssuedToken(String token, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedToken[expiresAt=" + expiresAt + "]";
    }
}
