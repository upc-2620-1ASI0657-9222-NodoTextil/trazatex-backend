package com.nodotextil.trazatex.organizationaccess.application.port.out;

import com.nodotextil.trazatex.organizationaccess.domain.model.Company;
import com.nodotextil.trazatex.organizationaccess.domain.model.Ruc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepositoryPort {

    void save(Company company);

    Optional<Company> findById(UUID id);

    Optional<Company> findByIdForUpdate(UUID id);

    boolean existsByRuc(Ruc ruc);

    long count();

    List<Company> findAll();
}
