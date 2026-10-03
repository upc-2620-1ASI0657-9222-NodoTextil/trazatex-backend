package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAdminRequestRepository extends JpaRepository<AdminRequestJpaEntity, UUID> {

	List<AdminRequestJpaEntity> findAllByOrderByCreatedAtDesc();
}
