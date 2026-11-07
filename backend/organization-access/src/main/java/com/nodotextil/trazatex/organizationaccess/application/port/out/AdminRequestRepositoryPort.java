package com.nodotextil.trazatex.organizationaccess.application.port.out;

import com.nodotextil.trazatex.organizationaccess.domain.model.AdminRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminRequestRepositoryPort {

    void save(AdminRequest request);

    Optional<AdminRequest> findByIdForUpdate(UUID id);

    List<AdminRequest> findAll();

    List<AdminRequest> findByCompanyId(UUID companyId);
}
