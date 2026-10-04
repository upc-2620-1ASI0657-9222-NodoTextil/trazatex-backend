package com.nodotextil.trazatex.organizationaccess.domain;

/** Base type of the exceptions raised by the Organization &amp; Access module. */
public abstract class OrganizationAccessException extends RuntimeException {

	protected OrganizationAccessException(String message) {
		super(message);
	}

	protected OrganizationAccessException(String message, Throwable cause) {
		super(message, cause);
	}
}
