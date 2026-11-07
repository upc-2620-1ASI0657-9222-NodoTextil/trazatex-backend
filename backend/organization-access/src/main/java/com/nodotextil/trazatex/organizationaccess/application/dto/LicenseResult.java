package com.nodotextil.trazatex.organizationaccess.application.dto;

import com.nodotextil.trazatex.organizationaccess.domain.model.LicenseUsage;

import java.util.List;
import java.util.UUID;

/** US01: límites contratados, empresas usadas y, por empresa, los cupos de usuario ocupados (ACTIVE + invitaciones PENDING vigentes). */
public record LicenseResult(
        int maxCompanies,
        int maxUsersPerCompany,
        long companiesUsed,
        long companiesAvailable,
        List<CompanyUsageResult> companies) {

    public record CompanyUsageResult(UUID companyId, String businessName, long usedSlots, int maxUsers, long availableSlots) {
    }

    public static LicenseResult from(LicenseUsage usage) {
        List<CompanyUsageResult> companies = usage.companies().stream()
                .map(c -> new CompanyUsageResult(c.companyId(), c.businessName(), c.usedSlots(), c.maxUsers(), c.availableSlots()))
                .toList();
        return new LicenseResult(usage.maxCompanies(), usage.maxUsersPerCompany(), usage.companiesUsed(),
                usage.companiesAvailable(), companies);
    }
}
