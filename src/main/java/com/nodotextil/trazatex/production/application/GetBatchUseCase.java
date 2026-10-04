package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.BatchRepository;
import java.util.UUID;

public class GetBatchUseCase {

    private final BatchRepository batchRepository;

    public GetBatchUseCase(BatchRepository batchRepository) {
        this.batchRepository = batchRepository;
    }

    public Batch execute(UUID id) {
        return batchRepository.findById(id).orElseThrow(() -> new BatchNotFoundException(id));
    }
}
