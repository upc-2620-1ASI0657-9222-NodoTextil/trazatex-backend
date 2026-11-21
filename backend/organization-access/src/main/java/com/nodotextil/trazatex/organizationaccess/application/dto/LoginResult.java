package com.nodotextil.trazatex.organizationaccess.application.dto;

import java.time.Instant;

public record LoginResult(String token, String tokenType, Instant expiresAt, UserResult user) {
}
