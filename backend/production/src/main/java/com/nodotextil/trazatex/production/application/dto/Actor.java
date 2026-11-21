package com.nodotextil.trazatex.production.application.dto;

import java.util.UUID;

public record Actor(UUID userId, UUID companyId, Role role) {
}
