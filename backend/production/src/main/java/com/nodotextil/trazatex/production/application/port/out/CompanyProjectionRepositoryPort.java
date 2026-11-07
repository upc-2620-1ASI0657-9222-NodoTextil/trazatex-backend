package com.nodotextil.trazatex.production.application.port.out;

import com.nodotextil.trazatex.production.domain.model.CompanyProjection;

import java.util.Optional;
import java.util.UUID;

/** Proyección local de empresas, alimentada por eventos de organization-access. */
public interface CompanyProjectionRepositoryPort {

    /** Inserta o actualiza. */
    void save(CompanyProjection company);

    Optional<CompanyProjection> findById(UUID companyId);

    /** Con bloqueo de fila: los consumidores de eventos serializan los cambios de una misma empresa. */
    Optional<CompanyProjection> findByIdForUpdate(UUID companyId);

    /** Las empresas {@code ACTIVE}, por razón social y, a igual nombre, por id (orden estable); una sola consulta más el conteo. */
    Page<CompanyProjection> findActive(int page, int size);
}
