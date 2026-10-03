package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.domain.UserStatus;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class OrganizationUserPersistenceAdapter implements OrganizationUserRepository {

	private final SpringDataOrganizationUserRepository repository;

	OrganizationUserPersistenceAdapter(SpringDataOrganizationUserRepository repository) {
		this.repository = repository;
	}

	@Override
	public OrganizationUser save(OrganizationUser user) {
		return repository.save(OrganizationUserJpaEntity.from(user)).toDomain();
	}

	@Override
	public Optional<OrganizationUser> findById(UUID id) {
		return repository.findById(id).map(OrganizationUserJpaEntity::toDomain);
	}

	@Override
	public Optional<OrganizationUser> findByEmail(String email) {
		if (email == null) {
			return Optional.empty();
		}
		return repository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
				.map(OrganizationUserJpaEntity::toDomain);
	}

	@Override
	public List<OrganizationUser> findByCompanyId(UUID companyId) {
		return toDomain(repository.findByCompanyIdOrderByCreatedAtAsc(companyId));
	}

	@Override
	public List<OrganizationUser> findActiveByCompanyId(UUID companyId) {
		return toDomain(repository.findByCompanyIdAndStatus(companyId, UserStatus.ACTIVE));
	}

	@Override
	public List<OrganizationUser> findActiveByCompanyIdAndRole(UUID companyId, Role role) {
		return toDomain(repository.findByCompanyIdAndStatusAndRole(companyId, UserStatus.ACTIVE,
				role));
	}

	@Override
	public long countActiveByCompanyId(UUID companyId) {
		return repository.countByCompanyIdAndStatus(companyId, UserStatus.ACTIVE);
	}

	private static List<OrganizationUser> toDomain(List<OrganizationUserJpaEntity> entities) {
		return entities.stream().map(OrganizationUserJpaEntity::toDomain).toList();
	}
}
