package com.nodotextil.trazatex.organizationaccess.application.port.out;

import com.nodotextil.trazatex.organizationaccess.domain.model.Company;
import com.nodotextil.trazatex.organizationaccess.domain.model.Ruc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepositoryPort {

    void save(Company company);

    Optional<Company> findById(UUID id);

    /**
     * Bloquea la fila de la empresa hasta el fin de la transacción: serializa todo lo que consume o libera cupos de usuario
     * de esa empresa (invitar, aprobar solicitud, reactivar), de modo que dos operaciones no ocupen el mismo último cupo.
     */
    Optional<Company> findByIdForUpdate(UUID id);

    boolean existsByRuc(Ruc ruc);

    long count();

    List<Company> findAll();
}
