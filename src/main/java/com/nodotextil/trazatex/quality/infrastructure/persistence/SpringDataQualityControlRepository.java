package com.nodotextil.trazatex.quality.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataQualityControlRepository
        extends JpaRepository<QualityControlJpaEntity, UUID> {

    boolean existsByBatchId(UUID batchId);

    boolean existsByBatchIdAndCompletedAtIsNull(UUID batchId);
}
