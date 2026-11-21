package com.nodotextil.trazatex.organizationaccess.domain.model;

public enum Role {
    LICENSE_OWNER,
    COMPANY_ADMIN,
    OPERATOR;

    public boolean belongsToCompany() {
        return this != LICENSE_OWNER;
    }
}
