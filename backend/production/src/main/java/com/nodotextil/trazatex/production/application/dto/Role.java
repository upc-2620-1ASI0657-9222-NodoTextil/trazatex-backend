package com.nodotextil.trazatex.production.application.dto;

public enum Role {
    LICENSE_OWNER,
    COMPANY_ADMIN,
    OPERATOR;

    public boolean belongsToCompany() {
        return this != LICENSE_OWNER;
    }
}
