package com.nodotextil.trazatex.traceability.infrastructure.web.security;

import java.util.UUID;

public record Actor(UUID userId, UUID companyId, Role role) {
}
