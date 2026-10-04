package com.nodotextil.trazatex.organizationaccess.domain;


public class ExternalServiceUnavailableException extends OrganizationAccessException {

	public ExternalServiceUnavailableException(String message) {
		super(message);
	}

	public ExternalServiceUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
