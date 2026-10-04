package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.BatchRepository;
import com.nodotextil.trazatex.production.domain.BatchSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PostgresBatchRepository implements BatchRepository {

    private final SpringDataBatchJpaRepository jpaRepository;

    PostgresBatchRepository(SpringDataBatchJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Batch save(Batch batch) {
        return BatchJpaMapper.toDomain(jpaRepository.save(BatchJpaMapper.toEntity(batch)));
    }

    @Override
    @Transactional
    public List<Batch> saveAll(List<Batch> batches) {
        List<BatchJpaEntity> entities = batches.stream()
                .map(BatchJpaMapper::toEntity)
                .toList();
        return jpaRepository.saveAll(entities).stream()
                .map(BatchJpaMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Batch> findById(UUID id) {
        return jpaRepository.findById(id).map(BatchJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Batch> findByQrCode(String qrCode) {
        return jpaRepository.findByQrCode(qrCode).map(BatchJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Batch> search(BatchSearchCriteria criteria) {
        Specification<BatchJpaEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.traceabilityId() != null) {
                predicates.add(builder.equal(
                        root.get("traceabilityId"), criteria.traceabilityId()));
            }
            if (criteria.materialType() != null) {
                predicates.add(builder.equal(root.get("materialType"), criteria.materialType()));
            }
            if (criteria.operationalPhase() != null) {
                predicates.add(builder.equal(
                        root.get("operationalPhase"), criteria.operationalPhase()));
            }
            if (criteria.registeredFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        root.get("registeredAt"), criteria.registeredFrom()));
            }
            if (criteria.registeredTo() != null) {
                predicates.add(builder.lessThanOrEqualTo(
                        root.get("registeredAt"), criteria.registeredTo()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };

        Sort sort = Sort.by(
                Sort.Order.asc("registeredAt"),
                Sort.Order.asc("id"));
        return jpaRepository.findAll(specification, sort).stream()
                .map(BatchJpaMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTraceabilityId(String traceabilityId) {
        return jpaRepository.existsByTraceabilityId(traceabilityId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByQrCode(String qrCode) {
        return jpaRepository.existsByQrCode(qrCode);
    }
}
