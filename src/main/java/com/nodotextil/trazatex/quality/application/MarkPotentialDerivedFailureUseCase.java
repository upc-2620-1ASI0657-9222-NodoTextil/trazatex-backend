package com.nodotextil.trazatex.quality.application;

import org.springframework.transaction.annotation.Transactional;
import com.nodotextil.trazatex.quality.application.contract.ProductionQualityPort;
import com.nodotextil.trazatex.quality.application.port.BatchQualityRepository;
import com.nodotextil.trazatex.quality.domain.BatchQuality;
import com.nodotextil.trazatex.quality.domain.InvalidQualityControlException;

import java.util.Objects;
import java.util.UUID;

public class MarkPotentialDerivedFailureUseCase {

    private final BatchQualityRepository batchQualityRepository;
    private final ProductionQualityPort productionQualityPort;

    public MarkPotentialDerivedFailureUseCase(
            BatchQualityRepository batchQualityRepository,
            ProductionQualityPort productionQualityPort) {

        this.batchQualityRepository = Objects.requireNonNull(batchQualityRepository);
        this.productionQualityPort = Objects.requireNonNull(productionQualityPort);
    }

    @Transactional
    public BatchQuality execute(UUID batchId) {
        Objects.requireNonNull(batchId, "Batch id is required");

        BatchQuality batchQuality = batchQualityRepository.findByBatchId(batchId)
                .orElseThrow(() -> new InvalidQualityControlException(
                        "Quality information was not found for the batch"
                ));

        switch (batchQuality.getStatus()) {
            case POTENTIAL_DERIVED_FAILURE -> {
                return batchQuality;
            }
            case NOT_REVIEWED, CONFORMING -> {
                batchQuality.markPotentialDerivedFailure();
                productionQualityPort.blockBatch(batchId);
                return batchQualityRepository.save(batchQuality);
            }
            case FAILED, PENDING_REEVALUATION -> {
                return batchQuality;
            }
        }
        return batchQuality;
    }
}
