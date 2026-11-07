package com.nodotextil.trazatex.production.application.port.in;

import com.nodotextil.trazatex.production.domain.model.CompanyStatus;

import java.util.UUID;

/** Mantiene la proyección local de empresas con los eventos de organization-access (nunca por REST). */
public interface SyncCompanyProjectionUseCase {

    /** {@code CompanyRegistered}: crea la empresa; si ya la conocía (por un cambio de estado) solo completa el nombre. */
    void registered(UUID companyId, String businessName, CompanyStatus status);

    /** {@code CompanyStatusChanged}: si la empresa no se conocía, la crea con ese estado (inactiva bloquea; ignorarla no). */
    void statusChanged(UUID companyId, CompanyStatus status);
}
