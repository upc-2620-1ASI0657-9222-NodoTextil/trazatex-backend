package com.nodotextil.trazatex.quality.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataBatchQualityRepository
        extends JpaRepository<BatchQualityJpaEntity, UUID> {
}
