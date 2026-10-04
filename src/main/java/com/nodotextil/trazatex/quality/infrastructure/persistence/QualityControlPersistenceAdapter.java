package com.nodotextil.trazatex.quality.infrastructure.persistence;

import com.nodotextil.trazatex.quality.application.port.QualityControlRepository;
import com.nodotextil.trazatex.quality.domain.QualityControl;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class QualityControlPersistenceAdapter implements QualityControlRepository {

    private final SpringDataQualityControlRepository repository;

    public QualityControlPersistenceAdapter(SpringDataQualityControlRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<QualityControl> findById(UUID controlId) {
        return repository.findById(controlId).map(QualityControlJpaEntity::toDomain);
    }

    @Override
    public boolean existsByBatchId(UUID batchId) {
        return repository.existsByBatchId(batchId);
    }

    @Override
    public boolean existsActiveByBatchId(UUID batchId) {
        return repository.existsByBatchIdAndCompletedAtIsNull(batchId);
    }

    @Override
    public QualityControl save(QualityControl qualityControl) {
        return repository.save(QualityControlJpaEntity.fromDomain(qualityControl)).toDomain();
    }
}
