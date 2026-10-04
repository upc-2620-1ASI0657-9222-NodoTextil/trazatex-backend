package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import java.util.Map;
import java.util.TreeMap;


final class RequiredFields {

	private RequiredFields() {
	}

	
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
