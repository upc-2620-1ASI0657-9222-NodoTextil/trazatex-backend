package com.nodotextil.trazatex.organizationaccess.domain.model;

import java.util.List;
import java.util.UUID;

public record LicenseUsage(
        int maxCompanies,
        int maxUsersPerCompany,
        long companiesUsed,
        List<CompanyUsage> companies) {

    public LicenseUsage {
        companies = List.copyOf(companies);
    }

    public long companiesAvailable() {
        return Math.max(0, maxCompanies - companiesUsed);
    }

    public record CompanyUsage(UUID companyId, String businessName, long usedSlots, int maxUsers) {

        public long availableSlots() {
            return Math.max(0, maxUsers - usedSlots);
        }
    }
}
