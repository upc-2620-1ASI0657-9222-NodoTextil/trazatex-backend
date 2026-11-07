package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.domain.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);


    boolean existsByEmail(String email);

    List<UserEntity> findByCompanyIdOrderByCreatedAtAsc(UUID companyId);

    long countByCompanyIdAndStatus(UUID companyId, UserStatus status);

    long countByCompanyIdAndRoleAndStatus(UUID companyId, Role role, UserStatus status);

    boolean existsByRoleAndStatus(Role role, UserStatus status);
}
