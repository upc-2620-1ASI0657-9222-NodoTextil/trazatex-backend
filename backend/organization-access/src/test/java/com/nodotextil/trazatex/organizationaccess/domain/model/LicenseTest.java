package com.nodotextil.trazatex.organizationaccess.domain.model;

import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LicenseTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    private final License license = License.create(UUID.randomUUID(), 3, 2, NOW);

    @Test
    void allowsCompaniesBelowTheLimitAndRejectsAtTheLimit() {
        license.ensureCanCreateCompany(2);
        assertThat(license.hasCompanyCapacity(2)).isTrue();

        assertThat(license.hasCompanyCapacity(3)).isFalse();
        assertThatThrownBy(() -> license.ensureCanCreateCompany(3))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("COMPANY_LIMIT_REACHED");
    }

    @Test
    void allowsUsersBelowTheLimitAndRejectsAtTheLimit() {
        license.ensureUserCapacity(1);

        assertThatThrownBy(() -> license.ensureUserCapacity(2))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("USER_LIMIT_REACHED");
    }

    @Test
    void limitsMustBeAtLeastOne() {
        assertThatThrownBy(() -> License.create(UUID.randomUUID(), 0, 2, NOW)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> License.create(UUID.randomUUID(), 3, 0, NOW)).isInstanceOf(ValidationException.class);
    }

    @Test
    void usageViewReportsLimitsAndAvailability() {
        UUID companyId = UUID.randomUUID();
        LicenseUsage usage = license.usage(2, List.of(new LicenseUsage.CompanyUsage(companyId, "Hilos SAC", 1, 2)));

        assertThat(usage.maxCompanies()).isEqualTo(3);
        assertThat(usage.companiesUsed()).isEqualTo(2);
        assertThat(usage.companiesAvailable()).isEqualTo(1);
        assertThat(usage.companies()).singleElement().satisfies(company -> {
            assertThat(company.usedSlots()).isEqualTo(1);
            assertThat(company.availableSlots()).isEqualTo(1);
        });
    }
}
