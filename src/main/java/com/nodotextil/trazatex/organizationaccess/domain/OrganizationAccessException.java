package com.nodotextil.trazatex.organizationaccess.domain;


public abstract class OrganizationAccessException extends RuntimeException {

	protected OrganizationAccessException(String message) {
		super(message);
	}

	protected OrganizationAccessException(String message, Throwable cause) {
		super(message, cause);
	}
}
