package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.port.out.CompanyProjectionRepositoryPort;
import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.production.domain.model.CompanyProjection;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * RF-003: una empresa {@code INACTIVE} no inicia nuevas operaciones productivas (409 {@code COMPANY_INACTIVE}). Se exige
 * en las que inician trabajo o incorporan material (registrar, dividir, iniciar transformación o transferencia, aceptar
 * una transferencia entrante) y <b>no</b> en las que cierran trabajo ya iniciado (finalizar una transformación, rechazar
 * una transferencia entrante), para que ningún lote quede atascado. Una empresa que aún no está en la proyección
 * (evento sin consumir) se trata como desconocida: 409 {@code COMPANY_UNKNOWN}.
 */
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
