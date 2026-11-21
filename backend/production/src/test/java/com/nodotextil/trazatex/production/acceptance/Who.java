package com.nodotextil.trazatex.production.acceptance;

import java.util.UUID;

public record Who(UUID userId, String role, UUID companyId) {
}
