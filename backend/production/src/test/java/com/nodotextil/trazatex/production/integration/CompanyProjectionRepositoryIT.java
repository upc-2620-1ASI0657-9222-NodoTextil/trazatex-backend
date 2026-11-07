package com.nodotextil.trazatex.production.integration;

import com.nodotextil.trazatex.production.domain.model.CompanyProjection;
import com.nodotextil.trazatex.production.domain.model.CompanyStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nodotextil.trazatex.production.domain.Fixtures.COMPANY;
import static org.assertj.core.api.Assertions.assertThat;

class CompanyProjectionRepositoryIT extends IntegrationTestBase {

    @Test
    void savingInsertsAndThenUpdatesTheSameRow() {
        inTx(() -> companies.save(CompanyProjection.of(COMPANY, "Hilos SAC", CompanyStatus.ACTIVE, START)));
        assertThat(count("SELECT count(*) FROM company_projection")).isEqualTo(1);

        inTx(() -> {
            CompanyProjection company = companies.findByIdForUpdate(COMPANY).orElseThrow();
            company.changeStatus(CompanyStatus.INACTIVE, START.plusSeconds(60));
            companies.save(company);
        });

        CompanyProjection loaded = inTx(() -> companies.findById(COMPANY).orElseThrow());
        assertThat(count("SELECT count(*) FROM company_projection")).isEqualTo(1);
        assertThat(loaded.status()).isEqualTo(CompanyStatus.INACTIVE);
        assertThat(loaded.isActive()).isFalse();
        assertThat(loaded.businessName()).isEqualTo("Hilos SAC");
        assertThat(loaded.updatedAt()).isEqualTo(START.plusSeconds(60));
    }

    @Test
    void anUnknownCompanyIsNotFound() {
        assertThat(inTx(() -> companies.findById(UUID.randomUUID()))).isEmpty();
        assertThat(inTx(() -> companies.findByIdForUpdate(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void renamingIsPersisted() {
        inTx(() -> companies.save(CompanyProjection.of(COMPANY, "Hilos SAC", CompanyStatus.ACTIVE, START)));
        inTx(() -> {
            CompanyProjection company = companies.findById(COMPANY).orElseThrow();
            company.rename("Hilos del Sur SAC", START.plusSeconds(5));
            companies.save(company);
        });
        assertThat(inTx(() -> companies.findById(COMPANY).orElseThrow()).businessName()).isEqualTo("Hilos del Sur SAC");
    }
}
