package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import java.util.Map;
import java.util.TreeMap;

/** Input checks shared by the use cases. */
final class RequiredFields {

	private RequiredFields() {
	}

	/** Fails with one field error per blank value, listed in a stable order. */
	static void require(String message, Map<String, String> fields) {
		Map<String, String> missing = new TreeMap<>();
		fields.forEach((field, value) -> {
			if (value == null || value.isBlank()) {
				missing.put(field, "is required");
			}
		});
		if (!missing.isEmpty()) {
			throw new OrganizationValidationException(message, missing);
		}
	}
}
