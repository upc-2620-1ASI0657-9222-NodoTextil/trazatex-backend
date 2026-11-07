package com.nodotextil.trazatex.organizationaccess.domain.model;

import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final Ruc RUC = new Ruc("20100070970");

    private Company newCompany() {
        return Company.register(UUID.randomUUID(), RUC, "  Hilos del Sur SAC ", List.of(TextileActivity.SPINNING, TextileActivity.DYEING), NOW);
    }

    @Test
    void registersActiveWithTrimmedNameAndActivities() {
        Company company = newCompany();

        assertThat(company.status()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(company.isActive()).isTrue();
        assertThat(company.businessName()).isEqualTo("Hilos del Sur SAC");
        assertThat(company.activities()).containsExactlyInAnyOrder(TextileActivity.SPINNING, TextileActivity.DYEING);
        assertThat(company.createdAt()).isEqualTo(NOW);
    }

    @Test
    void requiresBusinessNameAndAtLeastOneActivity() {
        assertThatThrownBy(() -> Company.register(UUID.randomUUID(), RUC, " ", List.of(), NOW))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.details()).containsExactly("businessName", "activities"));
    }

    @Test
    void deactivateAndActivateKeepTheData() {
        Company company = newCompany();

        company.deactivate();
        assertThat(company.status()).isEqualTo(CompanyStatus.INACTIVE);
        assertThat(company.businessName()).isEqualTo("Hilos del Sur SAC");
        assertThat(company.ruc()).isEqualTo(RUC);

        company.activate();
        assertThat(company.isActive()).isTrue();
    }

    @Test
    void repeatingTheSameTransitionConflicts() {
        Company company = newCompany();

        assertThatThrownBy(company::activate).isInstanceOf(ConflictException.class);
        company.deactivate();
        assertThatThrownBy(company::deactivate).isInstanceOf(ConflictException.class);
    }

    @Test
    void activitiesCannotBeChangedFromOutside() {
        Company company = newCompany();
        company.activities().clear();
        assertThat(company.activities()).hasSize(2);
    }
}
