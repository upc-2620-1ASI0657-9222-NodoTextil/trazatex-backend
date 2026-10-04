package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.BatchRepository;
import com.nodotextil.trazatex.production.domain.BatchSearchCriteria;
import java.util.List;

public class SearchBatchesUseCase {

    private final BatchRepository batchRepository;

    public SearchBatchesUseCase(BatchRepository batchRepository) {
        this.batchRepository = batchRepository;
    }

    public List<Batch> execute(BatchSearchCriteria criteria) {
        return batchRepository.search(criteria);
    }
}
