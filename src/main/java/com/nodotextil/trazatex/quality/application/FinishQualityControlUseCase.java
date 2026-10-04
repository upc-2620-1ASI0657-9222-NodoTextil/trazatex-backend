package com.nodotextil.trazatex.quality.application;

import com.nodotextil.trazatex.quality.application.contract.ProductionQualityPort;
import com.nodotextil.trazatex.quality.application.port.BatchQualityRepository;
import com.nodotextil.trazatex.quality.application.port.FailureRepository;
import com.nodotextil.trazatex.quality.application.port.QualityControlRepository;
import com.nodotextil.trazatex.quality.domain.BatchQuality;
import com.nodotextil.trazatex.quality.domain.Failure;
import com.nodotextil.trazatex.quality.domain.InvalidQualityControlException;
import com.nodotextil.trazatex.quality.domain.QualityControl;
import com.nodotextil.trazatex.quality.domain.QualityStatus;

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

        QualityStatus previousStatus = batchQuality.getStatus();
        LocalDateTime completedAt = LocalDateTime.now();

        control.complete(completedAt);

        // Production owns the operational phase. Finishing the control returns the
        // batch from "in quality control" to its normal operational phase.
        productionQualityPort.finishQualityControl(control.getBatchId());

        if (control.hasFailedTests()) {
            handleFailedControl(control, batchQuality, previousStatus, completedAt);
        } else {
            handleConformingControl(batchQuality, previousStatus);
        }

        qualityControlRepository.save(control);
        batchQualityRepository.save(batchQuality);

        return control;
    }

    private void handleFailedControl(
            QualityControl control,
            BatchQuality batchQuality,
            QualityStatus previousStatus,
            LocalDateTime completedAt) {

        batchQuality.markFailed();
        productionQualityPort.blockBatch(control.getBatchId());

        if (previousStatus == QualityStatus.PENDING_REEVALUATION) {
            Failure failure = failureRepository.findActiveByBatchId(control.getBatchId())
                    .orElseThrow(() -> new InvalidQualityControlException(
                            "Active failure was not found for the reevaluated batch"
                    ));

            failure.returnToPendingDecision();
            failureRepository.save(failure);
            return;
        }

        Failure failure = new Failure(
                UUID.randomUUID(),
                control.getId(),
                control.getBatchId(),
                control.getFailedTestIds(),
                completedAt
        );

        failureRepository.save(failure);
    }

    private void handleConformingControl(
            BatchQuality batchQuality,
            QualityStatus previousStatus) {

        batchQuality.markConforming();

        if (previousStatus == QualityStatus.PENDING_REEVALUATION) {
            Failure failure = failureRepository.findActiveByBatchId(batchQuality.getBatchId())
                    .orElseThrow(() -> new InvalidQualityControlException(
                            "Active failure was not found for the reevaluated batch"
                    ));

            failure.resolve();
            failureRepository.save(failure);
            productionQualityPort.unblockBatch(batchQuality.getBatchId());
        }

        if (previousStatus == QualityStatus.POTENTIAL_DERIVED_FAILURE) {
            productionQualityPort.unblockBatch(batchQuality.getBatchId());
        }
    }
}
