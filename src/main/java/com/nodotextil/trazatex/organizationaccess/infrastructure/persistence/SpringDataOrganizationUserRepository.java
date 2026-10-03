package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.domain.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataOrganizationUserRepository
		extends JpaRepository<OrganizationUserJpaEntity, UUID> {

	Optional<OrganizationUserJpaEntity> findByEmail(String email);

	List<OrganizationUserJpaEntity> findByCompanyIdOrderByCreatedAtAsc(UUID companyId);

	List<OrganizationUserJpaEntity> findByCompanyIdAndStatus(UUID companyId, UserStatus status);

	List<OrganizationUserJpaEntity> findByCompanyIdAndStatusAndRole(UUID companyId,
			UserStatus status, Role role);

	long countByCompanyIdAndStatus(UUID companyId, UserStatus status);
}
