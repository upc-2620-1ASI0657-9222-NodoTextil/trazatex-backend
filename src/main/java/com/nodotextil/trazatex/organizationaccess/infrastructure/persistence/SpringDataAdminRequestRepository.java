package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.domain.AdminRequestStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAdminRequestRepository extends JpaRepository<AdminRequestJpaEntity, UUID> {

	List<AdminRequestJpaEntity> findAllByOrderByCreatedAtDesc();

	boolean existsByCompanyIdAndEmailAndStatus(UUID companyId, String email,
			AdminRequestStatus status);
}
