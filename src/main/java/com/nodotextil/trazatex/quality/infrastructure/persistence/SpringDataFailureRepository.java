package com.nodotextil.trazatex.quality.infrastructure.persistence;

import com.nodotextil.trazatex.quality.domain.FailureStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

interface SpringDataFailureRepository extends JpaRepository<FailureJpaEntity, UUID> {

    Optional<FailureJpaEntity> findFirstByBatchIdAndStatusIn(
            UUID batchId,
            Collection<FailureStatus> statuses
    );
}
