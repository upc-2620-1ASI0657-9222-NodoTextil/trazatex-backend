package com.nodotextil.trazatex.organizationaccess.domain;

import java.util.Map;


public class OrganizationValidationException extends OrganizationAccessException {

	private final Map<String, String> fieldErrors;

	public OrganizationValidationException(String message) {
		this(message, Map.of());
	}

	public OrganizationValidationException(String message, Map<String, String> fieldErrors) {
		super(message);
		this.fieldErrors = Map.copyOf(fieldErrors);
	}

	public Map<String, String> fieldErrors() {
		return fieldErrors;
	}
}
