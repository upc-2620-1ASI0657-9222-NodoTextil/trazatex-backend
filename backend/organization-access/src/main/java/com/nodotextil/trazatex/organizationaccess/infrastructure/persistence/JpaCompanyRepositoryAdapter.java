package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.application.port.out.CompanyRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.domain.model.Company;
import com.nodotextil.trazatex.organizationaccess.domain.model.Ruc;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class JpaCompanyRepositoryAdapter implements CompanyRepositoryPort {

    private final CompanyJpaRepository repository;

    JpaCompanyRepositoryAdapter(CompanyJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(Company company) {
        CompanyEntity entity = new CompanyEntity();
        entity.id = company.id();
        entity.ruc = company.ruc().value();
        entity.businessName = company.businessName();
        entity.status = company.status();
        entity.createdAt = company.createdAt();
        entity.activities = EnumSet.copyOf(company.activities());
        repository.saveAndFlush(entity);
    }

    @Override
    public Optional<Company> findById(UUID id) {
        return repository.findById(id).map(JpaCompanyRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Company> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(JpaCompanyRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsByRuc(Ruc ruc) {
        return repository.existsByRuc(ruc.value());
    }

    @Override
    public long count() {
        return repository.count();
    }

    @Override
    public List<Company> findAll() {
        return repository.findAllByOrderByCreatedAtAsc().stream().map(JpaCompanyRepositoryAdapter::toDomain).toList();
    }

    private static Company toDomain(CompanyEntity entity) {
        return Company.restore(entity.id, new Ruc(entity.ruc), entity.businessName, entity.activities, entity.status,
                entity.createdAt);
    }
}
