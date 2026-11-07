package com.nodotextil.trazatex.organizationaccess.domain.model;

import java.util.List;
import java.util.UUID;

/** Vista de uso de la licencia (US01): límites, empresas usadas y, por empresa, los cupos de usuario ocupados. */
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

    /** {@code usedSlots} = usuarios ACTIVE + invitaciones PENDING vigentes. */
    public record CompanyUsage(UUID companyId, String businessName, long usedSlots, int maxUsers) {

        public long availableSlots() {
            return Math.max(0, maxUsers - usedSlots);
        }
    }
}
