package com.nodotextil.trazatex.organizationaccess.application.port.out;

import com.nodotextil.trazatex.organizationaccess.domain.model.License;

import java.util.Optional;

public interface LicenseRepositoryPort {

    Optional<License> find();

    /** Bloquea la fila de la licencia hasta el fin de la transacción: serializa la creación de empresas (límite de empresas). */
    Optional<License> findForUpdate();

    void save(License license);
}
