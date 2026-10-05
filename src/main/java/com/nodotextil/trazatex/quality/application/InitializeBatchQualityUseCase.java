package com.nodotextil.trazatex.quality.application;

import org.springframework.transaction.annotation.Transactional;
import com.nodotextil.trazatex.quality.application.port.BatchQualityRepository;
import com.nodotextil.trazatex.quality.domain.BatchQuality;
import java.util.UUID;

public class InitializeBatchQualityUseCase {

    private final BatchQualityRepository repository;

    public InitializeBatchQualityUseCase(BatchQualityRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public BatchQuality execute(UUID batchId) {
        return repository.findByBatchId(batchId)
                .orElseGet(() -> repository.save(new BatchQuality(batchId)));
    }
}
