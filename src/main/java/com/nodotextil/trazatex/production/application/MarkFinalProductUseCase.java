package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.BatchRepository;
import com.nodotextil.trazatex.production.domain.InvalidBatchException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class MarkFinalProductUseCase {

    private final BatchRepository batches;

    public MarkFinalProductUseCase(BatchRepository batches) {
        this.batches = batches;
    }

    @Transactional
    public Batch execute(UUID batchId, UUID companyId, Command command) {
        Batch batch = batches.findById(batchId)
                .orElseThrow(() -> new BatchNotFoundException(batchId));
        if (!batch.responsibleCompanyId().equals(companyId)) {
            throw new InvalidBatchException("Batch belongs to another company");
        }
        batch.markAsFinalProduct(
                command.buyerOrDistributor(),
                command.price(),
                command.currency(),
                command.commercialDate(),
                command.commercialReference());
        return batches.save(batch);
    }

    public record Command(
            String buyerOrDistributor,
            BigDecimal price,
            String currency,
            LocalDate commercialDate,
            String commercialReference) {
    }
}
