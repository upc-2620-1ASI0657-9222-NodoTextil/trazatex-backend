package com.nodotextil.trazatex.quality.application.dto;

import java.util.UUID;

public record Actor(UUID userId, UUID companyId, Role role) {
}
