package com.nodotextil.trazatex.shared.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAuditRecordRepository extends JpaRepository<AuditRecordJpaEntity, UUID> {

    List<AuditRecordJpaEntity> findByCompanyIdOrderByOccurredAtDesc(UUID companyId);

    List<AuditRecordJpaEntity> findAllByOrderByOccurredAtDesc();
}
