package com.nodotextil.trazatex.organizationaccess.domain.event;

import com.nodotextil.trazatex.organizationaccess.domain.model.Company;
import com.nodotextil.trazatex.organizationaccess.domain.model.CompanyStatus;

import java.util.UUID;

/** Tópico {@code orgaccess.company}, clave companyId. Lo consume production. */
public record CompanyStatusChanged(UUID companyId, CompanyStatus status) implements OrganizationEvent {

    public static final String TYPE = "CompanyStatusChanged";

    public static CompanyStatusChanged of(Company company) {
        return new CompanyStatusChanged(company.id(), company.status());
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String aggregateId() {
        return companyId.toString();
    }
}
