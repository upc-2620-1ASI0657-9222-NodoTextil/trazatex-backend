package com.nodotextil.trazatex.production.application.port.out;

import com.nodotextil.trazatex.production.domain.model.CompanyProjection;

import java.util.Optional;
import java.util.UUID;

public interface CompanyProjectionRepositoryPort {

    void save(CompanyProjection company);

    Optional<CompanyProjection> findById(UUID companyId);

    Optional<CompanyProjection> findByIdForUpdate(UUID companyId);

    Page<CompanyProjection> findActive(int page, int size);
}
