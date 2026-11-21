package com.nodotextil.trazatex.organizationaccess.application.usecase;

import com.nodotextil.trazatex.organizationaccess.application.port.out.CompanyRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.organizationaccess.domain.model.Company;
import com.nodotextil.trazatex.sharedkernel.exception.ForbiddenException;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class CompanyGuard {

    private final CompanyRepositoryPort companies;

    CompanyGuard(CompanyRepositoryPort companies) {
        this.companies = companies;
    }

    Company requireActive(UUID companyId) {
        Company company = companies.findById(companyId).orElseThrow(() -> notFound(companyId));
        ensureActive(company);
        return company;
    }

    Company lockCompany(UUID companyId) {
        return companies.findByIdForUpdate(companyId).orElseThrow(() -> notFound(companyId));
    }

    Company lockActive(UUID companyId) {
        Company company = companies.findByIdForUpdate(companyId).orElseThrow(() -> notFound(companyId));
        ensureActive(company);
        return company;
    }

    private static void ensureActive(Company company) {
        if (!company.isActive()) {
            throw new ForbiddenException(ErrorCodes.COMPANY_INACTIVE,
                    "La empresa está inactiva: sus usuarios solo pueden consultar.");
        }
    }

    private static NotFoundException notFound(UUID companyId) {
        return new NotFoundException("La empresa no existe.");
    }
}
