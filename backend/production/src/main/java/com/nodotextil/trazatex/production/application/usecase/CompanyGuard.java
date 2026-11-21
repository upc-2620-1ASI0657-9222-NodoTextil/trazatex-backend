package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.port.out.CompanyProjectionRepositoryPort;
import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.production.domain.model.CompanyProjection;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
class CompanyGuard {

    private final CompanyProjectionRepositoryPort companies;

    CompanyGuard(CompanyProjectionRepositoryPort companies) {
        this.companies = companies;
    }

    void requireActive(UUID companyId) {
        CompanyProjection company = companies.findById(companyId).orElseThrow(() -> new ConflictException(
                ErrorCodes.COMPANY_UNKNOWN, "La empresa aún no está sincronizada: reintente en unos segundos."));
        company.ensureActive();
    }

    Optional<CompanyProjection> find(UUID companyId) {
        return companies.findById(companyId);
    }
}
