package com.nodotextil.trazatex.production.infrastructure.persistence;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface SpringDataBatchJpaRepository
        extends JpaRepository<BatchJpaEntity, UUID>,
                JpaSpecificationExecutor<BatchJpaEntity> {

    boolean existsByTraceabilityId(String traceabilityId);

    boolean existsByQrCode(String qrCode);

    Optional<BatchJpaEntity> findByQrCode(String qrCode);
}
