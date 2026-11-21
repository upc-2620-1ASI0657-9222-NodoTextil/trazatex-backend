package com.nodotextil.trazatex.organizationaccess.application.dto;

import com.nodotextil.trazatex.organizationaccess.domain.model.Role;

import java.util.UUID;

public record Actor(UUID userId, UUID companyId, Role role) {
}
