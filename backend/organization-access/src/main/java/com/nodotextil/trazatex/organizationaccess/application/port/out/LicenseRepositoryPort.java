package com.nodotextil.trazatex.organizationaccess.application.port.out;

import com.nodotextil.trazatex.organizationaccess.domain.model.License;

import java.util.Optional;

public interface LicenseRepositoryPort {

    Optional<License> find();

    Optional<License> findForUpdate();

    void save(License license);
}
