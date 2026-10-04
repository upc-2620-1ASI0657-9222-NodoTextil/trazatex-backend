package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.ProductionEvidenceOwnerType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataProductionEvidenceJpaRepository
        extends JpaRepository<ProductionEvidenceJpaEntity, UUID> {

    List<ProductionEvidenceJpaEntity> findByOwnerTypeAndOwnerIdOrderByUploadedAtAsc(
            ProductionEvidenceOwnerType ownerType,
            UUID ownerId);
}
