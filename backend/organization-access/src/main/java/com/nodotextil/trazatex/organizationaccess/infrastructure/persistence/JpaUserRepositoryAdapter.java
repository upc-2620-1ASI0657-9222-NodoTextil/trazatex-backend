package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.application.port.out.UserRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.domain.model.Email;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.domain.model.User;
import com.nodotextil.trazatex.organizationaccess.domain.model.UserStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class JpaUserRepositoryAdapter implements UserRepositoryPort {

    private final UserJpaRepository repository;

    JpaUserRepositoryAdapter(UserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(User user) {
        UserEntity entity = new UserEntity();
        entity.id = user.id();
        entity.companyId = user.companyId();
        entity.email = user.email().value();
        entity.firstName = user.firstName();
        entity.lastName = user.lastName();
        entity.position = user.position();
        entity.passwordHash = user.passwordHash();
        entity.role = user.role();
        entity.status = user.status();
        entity.createdAt = user.createdAt();
        repository.saveAndFlush(entity);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return repository.findById(id).map(JpaUserRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return repository.findByEmail(email.value()).map(JpaUserRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return repository.existsByEmail(email.value());
    }

    @Override
    public List<User> findByCompanyId(UUID companyId) {
        return repository.findByCompanyIdOrderByCreatedAtAsc(companyId).stream().map(JpaUserRepositoryAdapter::toDomain).toList();
    }

    @Override
    public long countActiveByCompany(UUID companyId) {
        return repository.countByCompanyIdAndStatus(companyId, UserStatus.ACTIVE);
    }

    @Override
    public long countActiveAdminsByCompany(UUID companyId) {
        return repository.countByCompanyIdAndRoleAndStatus(companyId, Role.COMPANY_ADMIN, UserStatus.ACTIVE);
    }

    @Override
    public boolean existsActiveOwner() {
        return repository.existsByRoleAndStatus(Role.LICENSE_OWNER, UserStatus.ACTIVE);
    }

    private static User toDomain(UserEntity entity) {
        return User.restore(entity.id, entity.companyId, new Email(entity.email), entity.firstName, entity.lastName,
                entity.position, entity.passwordHash, entity.role, entity.status, entity.createdAt);
    }
}
