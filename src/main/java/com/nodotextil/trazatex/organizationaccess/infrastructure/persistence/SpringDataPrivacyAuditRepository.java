package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPrivacyAuditRepository extends JpaRepository<PrivacyAuditJpaEntity, UUID> {
}
