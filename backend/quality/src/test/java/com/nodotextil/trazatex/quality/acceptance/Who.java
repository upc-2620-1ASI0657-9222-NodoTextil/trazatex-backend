package com.nodotextil.trazatex.quality.acceptance;

import java.util.UUID;

public record Who(UUID userId, String role, UUID companyId) {
}
