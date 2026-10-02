package com.nodotextil.trazatex.quality.application;

import com.nodotextil.trazatex.quality.application.contract.ProductionQualityPort;
import com.nodotextil.trazatex.quality.application.port.BatchQualityRepository;
import com.nodotextil.trazatex.quality.application.port.FailureRepository;
import com.nodotextil.trazatex.quality.application.port.QualityControlRepository;
import com.nodotextil.trazatex.quality.domain.BatchQuality;
import com.nodotextil.trazatex.quality.domain.Failure;
import com.nodotextil.trazatex.quality.domain.InvalidQualityControlException;
import com.nodotextil.trazatex.quality.domain.QualityControl;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public final class FinishQualityControlUseCase {

    private final QualityControlRepository qualityControlRepository;
    private final BatchQualityRepository batchQualityRepository;
    private final FailureRepository failureRepository;
    private final ProductionQualityPort productionQualityPort;

    public FinishQualityControlUseCase(
            QualityControlRepository qualityControlRepository,
            BatchQualityRepository batchQualityRepository,
            FailureRepository failureRepository,
            ProductionQualityPort productionQualityPort) {

        this.qualityControlRepository = Objects.requireNonNull(qualityControlRepository);
        this.batchQualityRepository = Objects.requireNonNull(batchQualityRepository);
        this.failureRepository = Objects.requireNonNull(failureRepository);
        this.productionQualityPort = Objects.requireNonNull(productionQualityPort);
    }

    public QualityControl execute(UUID controlId) {
        Objects.requireNonNull(controlId, "Control id is required");

        QualityControl control = qualityControlRepository.findById(controlId)
                .orElseThrow(() -> new InvalidQualityControlException(
                        "Quality control was not found"
                ));

        BatchQuality batchQuality = batchQualityRepository.findByBatchId(control.getBatchId())
                .orElseThrow(() -> new InvalidQualityControlException(
                        "Quality information was not found for the batch"
                ));

        LocalDateTime completedAt = LocalDateTime.now();
        control.complete(completedAt);

        if (control.hasFailedTests()) {
            batchQuality.markFailed();

            Failure failure = new Failure(
                    UUID.randomUUID(),
                    control.getId(),
                    control.getBatchId(),
                    control.getFailedTestIds(),
                    completedAt
            );

            failureRepository.save(failure);
        } else {
            batchQuality.markConforming();
        }

        productionQualityPort.finishQualityControl(control.getBatchId());

        qualityControlRepository.save(control);
        batchQualityRepository.save(batchQuality);

        return control;
    }
}
