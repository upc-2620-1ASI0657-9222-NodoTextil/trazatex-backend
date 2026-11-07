package com.nodotextil.trazatex.organizationaccess.application.dto;

import com.nodotextil.trazatex.organizationaccess.domain.model.Company;
import com.nodotextil.trazatex.organizationaccess.domain.model.CompanyStatus;
import com.nodotextil.trazatex.organizationaccess.domain.model.TextileActivity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CompanyResult(
        UUID id,
        String ruc,
        String businessName,
        List<TextileActivity> activities,
        CompanyStatus status,
        Instant createdAt) {

    public static CompanyResult from(Company company) {
        return new CompanyResult(company.id(), company.ruc().value(), company.businessName(),
                List.copyOf(company.activities()), company.status(), company.createdAt());
    }
}
