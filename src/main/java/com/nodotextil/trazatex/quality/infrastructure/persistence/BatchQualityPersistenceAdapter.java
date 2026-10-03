package com.nodotextil.trazatex.quality.infrastructure.persistence;

import com.nodotextil.trazatex.quality.application.port.BatchQualityRepository;
import com.nodotextil.trazatex.quality.domain.BatchQuality;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class BatchQualityPersistenceAdapter implements BatchQualityRepository {

    private final SpringDataBatchQualityRepository repository;

    public BatchQualityPersistenceAdapter(SpringDataBatchQualityRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<BatchQuality> findByBatchId(UUID batchId) {
        return repository.findById(batchId).map(BatchQualityJpaEntity::toDomain);
    }

    @Override
    public BatchQuality save(BatchQuality batchQuality) {
        return repository.save(BatchQualityJpaEntity.fromDomain(batchQuality)).toDomain();
    }
}
