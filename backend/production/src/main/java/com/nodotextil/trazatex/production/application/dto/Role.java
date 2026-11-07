package com.nodotextil.trazatex.production.application.dto;

/** Roles del sistema (docs/domain/glossary.md). */
public enum Role {
    LICENSE_OWNER,
    COMPANY_ADMIN,
    OPERATOR;

    /** Solo el Dueño de Licencia no pertenece a una empresa. */
    public boolean belongsToCompany() {
        return this != LICENSE_OWNER;
    }
}
