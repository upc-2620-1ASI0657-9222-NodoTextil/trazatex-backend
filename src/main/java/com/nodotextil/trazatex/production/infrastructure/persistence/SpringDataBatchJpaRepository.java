package com.nodotextil.trazatex.production.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataBatchJpaRepository extends JpaRepository<BatchJpaEntity, UUID> {

    boolean existsByTraceabilityId(String traceabilityId);

    boolean existsByQrCode(String qrCode);
}
