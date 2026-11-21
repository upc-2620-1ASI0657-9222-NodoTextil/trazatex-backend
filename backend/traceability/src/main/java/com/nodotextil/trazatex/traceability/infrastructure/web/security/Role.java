package com.nodotextil.trazatex.traceability.infrastructure.web.security;

public enum Role {
    LICENSE_OWNER,
    COMPANY_ADMIN,
    OPERATOR;

    public boolean belongsToCompany() {
        return this != LICENSE_OWNER;
    }
}
