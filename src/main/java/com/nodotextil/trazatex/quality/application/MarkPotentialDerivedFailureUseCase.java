package com.nodotextil.trazatex.quality.application;

import com.nodotextil.trazatex.quality.application.contract.ProductionQualityPort;
import com.nodotextil.trazatex.quality.application.port.BatchQualityRepository;
import com.nodotextil.trazatex.quality.domain.BatchQuality;
import com.nodotextil.trazatex.quality.domain.InvalidQualityControlException;

import java.util.Objects;
import java.util.UUID;

public final class MarkPotentialDerivedFailureUseCase {

    private final BatchQualityRepository batchQualityRepository;
    private final ProductionQualityPort productionQualityPort;

    public MarkPotentialDerivedFailureUseCase(
            BatchQualityRepository batchQualityRepository,
            ProductionQualityPort productionQualityPort) {

        this.batchQualityRepository = Objects.requireNonNull(batchQualityRepository);
        this.productionQualityPort = Objects.requireNonNull(productionQualityPort);
    }

    public BatchQuality execute(UUID batchId) {
        Objects.requireNonNull(batchId, "Batch id is required");

        BatchQuality batchQuality = batchQualityRepository.findByBatchId(batchId)
                .orElseThrow(() -> new InvalidQualityControlException(
                        "Quality information was not found for the batch"
                ));

        batchQuality.markPotentialDerivedFailure();
        productionQualityPort.blockBatch(batchId);

        return batchQualityRepository.save(batchQuality);
    }
}
