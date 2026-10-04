package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.ProductionEvidence;
import com.nodotextil.trazatex.production.domain.ProductionEvidenceOwnerType;
import com.nodotextil.trazatex.production.domain.ProductionEvidenceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PostgresProductionEvidenceRepository implements ProductionEvidenceRepository {

    private final SpringDataProductionEvidenceJpaRepository jpaRepository;

    PostgresProductionEvidenceRepository(
            SpringDataProductionEvidenceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public ProductionEvidence save(ProductionEvidence evidence) {
        return ProductionEvidenceJpaMapper.toDomain(
                jpaRepository.save(ProductionEvidenceJpaMapper.toEntity(evidence)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductionEvidence> findByOwner(
            ProductionEvidenceOwnerType ownerType,
            UUID ownerId) {
        return jpaRepository.findByOwnerTypeAndOwnerIdOrderByUploadedAtAsc(ownerType, ownerId)
                .stream()
                .map(ProductionEvidenceJpaMapper::toDomain)
                .toList();
    }
}
