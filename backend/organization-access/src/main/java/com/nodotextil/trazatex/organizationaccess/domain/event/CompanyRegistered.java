package com.nodotextil.trazatex.organizationaccess.domain.event;

import com.nodotextil.trazatex.organizationaccess.domain.model.Company;
import com.nodotextil.trazatex.organizationaccess.domain.model.CompanyStatus;
import com.nodotextil.trazatex.organizationaccess.domain.model.TextileActivity;

import java.util.List;
import java.util.UUID;

/** Tópico {@code orgaccess.company}, clave companyId. Lo consume production. */
public record CompanyRegistered(
        UUID companyId,
        String ruc,
        String businessName,
        List<TextileActivity> activities,
        CompanyStatus status) implements OrganizationEvent {

    public static final String TYPE = "CompanyRegistered";

    public static CompanyRegistered of(Company company) {
        return new CompanyRegistered(company.id(), company.ruc().value(), company.businessName(),
                List.copyOf(company.activities()), company.status());
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
