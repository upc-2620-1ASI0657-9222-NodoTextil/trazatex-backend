package com.nodotextil.trazatex.production.infrastructure;

import com.nodotextil.trazatex.production.application.BatchNotFoundException;
import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.BatchRepository;
import com.nodotextil.trazatex.quality.application.contract.ProductionQualityPort;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ProductionQualityAdapter implements ProductionQualityPort {

    private final BatchRepository batchRepository;

    ProductionQualityAdapter(BatchRepository batchRepository) {
        this.batchRepository = batchRepository;
    }

    @Override
    @Transactional
    public void startQualityControl(UUID batchId) {
        Batch batch = findBatch(batchId);
        batch.startQualityControl();
        batchRepository.save(batch);
    }

    @Override
    @Transactional
    public void finishQualityControl(UUID batchId) {
        Batch batch = findBatch(batchId);
        batch.finishQualityControl();
        batchRepository.save(batch);
    }

    @Override
    @Transactional
    public void blockBatch(UUID batchId) {
        Batch batch = findBatch(batchId);
        batch.block();
        batchRepository.save(batch);
    }

    @Override
    @Transactional
    public void unblockBatch(UUID batchId) {
        Batch batch = findBatch(batchId);
        batch.unblock();
        batchRepository.save(batch);
    }

    @Override
    @Transactional
    public void discardBatch(UUID batchId) {
        Batch batch = findBatch(batchId);
        batch.discard();
        batchRepository.save(batch);
    }


    @Override
    @Transactional(readOnly = true)
    public UUID companyIdOf(UUID batchId) {
        return findBatch(batchId).responsibleCompanyId();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal quantityOf(UUID batchId) {
        return findBatch(batchId).quantityKg();
    }
    private Batch findBatch(UUID batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new BatchNotFoundException(batchId));
    }
}
