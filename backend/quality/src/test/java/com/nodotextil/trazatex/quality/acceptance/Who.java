package com.nodotextil.trazatex.quality.acceptance;

import java.util.UUID;

/** Quién llama, con la identidad que inyecta el Gateway ({@code X-User-Id}, {@code X-User-Role}, {@code X-Company-Id}). */
public record Who(UUID userId, String role, UUID companyId) {
}
