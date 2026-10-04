package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.BatchRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
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
    @Transactional(readOnly = true)
    public Optional<Batch> findById(UUID id) {
        return jpaRepository.findById(id).map(BatchJpaMapper::toDomain);
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
