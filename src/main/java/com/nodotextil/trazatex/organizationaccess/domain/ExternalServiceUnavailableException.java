package com.nodotextil.trazatex.organizationaccess.domain;

/** An external provider (SUNAT, Have I Been Pwned) could not be reached (HTTP 503). */
public class ExternalServiceUnavailableException extends OrganizationAccessException {

	public ExternalServiceUnavailableException(String message) {
		super(message);
	}

	public ExternalServiceUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
