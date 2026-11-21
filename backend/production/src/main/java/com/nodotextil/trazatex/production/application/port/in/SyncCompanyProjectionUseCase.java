package com.nodotextil.trazatex.production.application.port.in;

import com.nodotextil.trazatex.production.domain.model.CompanyStatus;

import java.util.UUID;

public interface SyncCompanyProjectionUseCase {

    void registered(UUID companyId, String businessName, CompanyStatus status);

    void statusChanged(UUID companyId, CompanyStatus status);
}
