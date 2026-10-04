package com.nodotextil.trazatex.organizationaccess.domain;

public enum Role {
	LICENSE_OWNER,
	COMPANY_ADMIN,
	OPERATOR;

	/** Every role except the license owner works inside a company. */
	public boolean belongsToCompany() {
		return this != LICENSE_OWNER;
	}

	/** Only company administrators and operators can be invited. */
	public boolean isInvitable() {
		return this == COMPANY_ADMIN || this == OPERATOR;
	}
}
