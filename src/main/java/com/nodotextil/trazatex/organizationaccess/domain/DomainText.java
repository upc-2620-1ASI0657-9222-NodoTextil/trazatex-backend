package com.nodotextil.trazatex.organizationaccess.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Small input-normalization helpers shared by the domain classes. */
final class DomainText {

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private DomainText() {
	}

	static String required(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new OrganizationValidationException(field + " is required");
		}
		return value.trim();
	}

	static String optional(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	static String email(String value) {
		String normalized = required(value, "email").toLowerCase(Locale.ROOT);
		if (!EMAIL.matcher(normalized).matches()) {
			throw new OrganizationValidationException("email is not valid");
		}
		return normalized;
	}

	static <T> T notNull(T value, String field) {
		if (value == null) {
			throw new OrganizationValidationException(field + " is required");
		}
		return value;
	}
}
