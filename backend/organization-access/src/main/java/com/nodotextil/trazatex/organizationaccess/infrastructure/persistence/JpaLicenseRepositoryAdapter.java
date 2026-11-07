package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.application.port.out.LicenseRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.domain.model.License;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class JpaLicenseRepositoryAdapter implements LicenseRepositoryPort {

    private final LicenseJpaRepository repository;

    JpaLicenseRepositoryAdapter(LicenseJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<License> find() {
        return repository.findAll().stream().findFirst().map(JpaLicenseRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<License> findForUpdate() {
        return repository.findAllForUpdate().stream().findFirst().map(JpaLicenseRepositoryAdapter::toDomain);
    }

    @Override
    public void save(License license) {
        LicenseEntity entity = new LicenseEntity();
        entity.id = license.id();
        entity.maxCompanies = license.maxCompanies();
        entity.maxUsersPerCompany = license.maxUsersPerCompany();
        entity.createdAt = license.createdAt();
        repository.saveAndFlush(entity);
    }

    private static License toDomain(LicenseEntity entity) {
        return License.restore(entity.id, entity.maxCompanies, entity.maxUsersPerCompany, entity.createdAt);
    }
}
