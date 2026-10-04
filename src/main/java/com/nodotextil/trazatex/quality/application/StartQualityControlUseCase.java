package com.nodotextil.trazatex.quality.application;

import org.springframework.transaction.annotation.Transactional;
import com.nodotextil.trazatex.quality.application.contract.ProductionQualityPort;
import com.nodotextil.trazatex.quality.application.port.BatchQualityRepository;
import com.nodotextil.trazatex.quality.application.port.QualityControlRepository;
import com.nodotextil.trazatex.quality.domain.BatchQuality;
import com.nodotextil.trazatex.quality.domain.ControlType;
import com.nodotextil.trazatex.quality.domain.InvalidQualityControlException;
import com.nodotextil.trazatex.quality.domain.QualityControl;
import com.nodotextil.trazatex.quality.domain.QualityStatus;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public final class StartQualityControlUseCase {

    private final QualityControlRepository qualityControlRepository;
    private final BatchQualityRepository batchQualityRepository;
    private final ProductionQualityPort productionQualityPort;

    public StartQualityControlUseCase(
            QualityControlRepository qualityControlRepository,
            BatchQualityRepository batchQualityRepository,
            ProductionQualityPort productionQualityPort) {

        this.qualityControlRepository = Objects.requireNonNull(qualityControlRepository);
        this.batchQualityRepository = Objects.requireNonNull(batchQualityRepository);
        this.productionQualityPort = Objects.requireNonNull(productionQualityPort);
    }

    @Transactional
    public QualityControl execute(UUID batchId) {
        Objects.requireNonNull(batchId, "Batch id is required");

        if (qualityControlRepository.existsActiveByBatchId(batchId)) {
            throw new InvalidQualityControlException(
                    "The batch already has an active quality control"
            );
        }

        BatchQuality batchQuality = batchQualityRepository.findByBatchId(batchId)
                .orElseThrow(() -> new InvalidQualityControlException(
                        "Quality information was not found for the batch"
                ));

        ControlType controlType = determineControlType(
                batchId,
                batchQuality.getStatus()
        );

        productionQualityPort.startQualityControl(batchId);

        QualityControl qualityControl = new QualityControl(
                UUID.randomUUID(),
                batchId,
                controlType,
                LocalDateTime.now()
        );

        return qualityControlRepository.save(qualityControl);
    }

    private ControlType determineControlType(UUID batchId, QualityStatus status) {
        return switch (status) {
            case NOT_REVIEWED -> ControlType.EVALUATION;
            case PENDING_REEVALUATION -> ControlType.REEVALUATION;
            case POTENTIAL_DERIVED_FAILURE ->
                    qualityControlRepository.existsByBatchId(batchId)
                            ? ControlType.REEVALUATION
                            : ControlType.EVALUATION;
            default -> throw new InvalidQualityControlException(
                    "A quality control cannot be started for status " + status
            );
        };
    }
}
