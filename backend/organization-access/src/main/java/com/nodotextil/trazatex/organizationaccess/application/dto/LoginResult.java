package com.nodotextil.trazatex.organizationaccess.application.dto;

import java.time.Instant;

/** JWT RS256 (ADR-005) y los datos del usuario. {@code tokenType} es siempre {@code Bearer}. */
public record LoginResult(String token, String tokenType, Instant expiresAt, UserResult user) {
}
