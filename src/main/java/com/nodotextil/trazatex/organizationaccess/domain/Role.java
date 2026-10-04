package com.nodotextil.trazatex.organizationaccess.domain;

public enum Role {
	LICENSE_OWNER,
	COMPANY_ADMIN,
	OPERATOR;

	
	public boolean belongsToCompany() {
		return this != LICENSE_OWNER;
	}

	
	public boolean isInvitable() {
		return this == COMPANY_ADMIN || this == OPERATOR;
	}
}
