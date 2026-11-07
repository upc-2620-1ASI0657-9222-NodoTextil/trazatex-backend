package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.application.port.out.CompanyProjectionRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.Page;
import com.nodotextil.trazatex.production.domain.model.CompanyProjection;
import com.nodotextil.trazatex.production.domain.model.CompanyStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
class JpaCompanyProjectionRepositoryAdapter implements CompanyProjectionRepositoryPort {

    private final CompanyProjectionJpaRepository repository;

    JpaCompanyProjectionRepositoryAdapter(CompanyProjectionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(CompanyProjection company) {
        CompanyProjectionEntity entity = repository.findById(company.companyId()).orElseGet(() -> {
            CompanyProjectionEntity created = new CompanyProjectionEntity();
            created.companyId = company.companyId();
            return created;
        });
        entity.businessName = company.businessName();
        entity.status = company.status();
        entity.updatedAt = company.updatedAt();
        repository.saveAndFlush(entity);
    }

    @Override
    public Optional<CompanyProjection> findById(UUID companyId) {
        return repository.findById(companyId).map(JpaCompanyProjectionRepositoryAdapter::toDomain);
    }

    @Override
    public Page<CompanyProjection> findActive(int page, int size) {
        org.springframework.data.domain.Page<CompanyProjectionEntity> found =
                repository.findByStatusOrderByBusinessNameAscCompanyIdAsc(CompanyStatus.ACTIVE, PageRequest.of(page, size));
        return new Page<>(found.getContent().stream().map(JpaCompanyProjectionRepositoryAdapter::toDomain).toList(), page, size, found.getTotalElements());
    }

    @Override
    public Optional<CompanyProjection> findByIdForUpdate(UUID companyId) {
        return repository.findByIdForUpdate(companyId).map(JpaCompanyProjectionRepositoryAdapter::toDomain);
    }

    private static CompanyProjection toDomain(CompanyProjectionEntity entity) {
        return CompanyProjection.restore(entity.companyId, entity.businessName, entity.status, entity.updatedAt);
    }
}
