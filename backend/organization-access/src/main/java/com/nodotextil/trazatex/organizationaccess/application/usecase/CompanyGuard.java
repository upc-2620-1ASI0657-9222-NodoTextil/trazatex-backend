package com.nodotextil.trazatex.organizationaccess.application.usecase;

import com.nodotextil.trazatex.organizationaccess.application.port.out.CompanyRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.organizationaccess.domain.model.Company;
import com.nodotextil.trazatex.sharedkernel.exception.ForbiddenException;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Los usuarios de una empresa INACTIVE pueden iniciar sesión y consultar, pero no escribir (decisión del usuario, fase 02).
 * {@link #lockActive} además bloquea la fila de la empresa para serializar las operaciones que usan sus cupos de usuario.
 */
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

    /** Bloquea la fila sin exigir que esté activa (aprobar una solicitud de administrador). */
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
