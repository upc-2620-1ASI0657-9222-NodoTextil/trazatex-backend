package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.application.port.out.AdminRequestRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.domain.model.AdminRequest;
import com.nodotextil.trazatex.organizationaccess.domain.model.Email;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class JpaAdminRequestRepositoryAdapter implements AdminRequestRepositoryPort {

    private final AdminRequestJpaRepository repository;

    JpaAdminRequestRepositoryAdapter(AdminRequestJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(AdminRequest request) {
        AdminRequestEntity entity = new AdminRequestEntity();
        entity.id = request.id();
        entity.companyId = request.companyId();
        entity.requestedBy = request.requestedBy();
        entity.fullName = request.fullName();
        entity.email = request.email().value();
        entity.position = request.position();
        entity.status = request.status();
        entity.createdAt = request.createdAt();
        entity.resolvedAt = request.resolvedAt();
        entity.resolvedBy = request.resolvedBy();
        repository.saveAndFlush(entity);
    }

    @Override
    public Optional<AdminRequest> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(JpaAdminRequestRepositoryAdapter::toDomain);
    }

    @Override
    public List<AdminRequest> findAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(JpaAdminRequestRepositoryAdapter::toDomain).toList();
    }

    @Override
    public List<AdminRequest> findByCompanyId(UUID companyId) {
        return repository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream().map(JpaAdminRequestRepositoryAdapter::toDomain).toList();
    }

    private static AdminRequest toDomain(AdminRequestEntity entity) {
        return AdminRequest.restore(entity.id, entity.companyId, entity.requestedBy, entity.fullName, new Email(entity.email),
                entity.position, entity.status, entity.createdAt, entity.resolvedAt, entity.resolvedBy);
    }
}
