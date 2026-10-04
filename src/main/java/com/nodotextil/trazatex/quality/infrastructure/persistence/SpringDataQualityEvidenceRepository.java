package com.nodotextil.trazatex.quality.infrastructure.persistence;

import com.nodotextil.trazatex.quality.domain.EvidenceOwnerType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataQualityEvidenceRepository extends JpaRepository<QualityEvidenceJpaEntity, UUID> {

    List<QualityEvidenceJpaEntity> findByOwnerTypeAndOwnerIdOrderByUploadedAtAsc(
            EvidenceOwnerType ownerType, UUID ownerId);
}
