package com.nodotextil.trazatex.quality.application;

import org.springframework.transaction.annotation.Transactional;
import com.nodotextil.trazatex.quality.application.contract.ProductionQualityPort;
import com.nodotextil.trazatex.quality.application.contract.QualityEventPublisher;
import com.nodotextil.trazatex.quality.application.port.BatchQualityRepository;
import com.nodotextil.trazatex.quality.application.port.FailureRepository;
import com.nodotextil.trazatex.quality.domain.BatchQuality;
import com.nodotextil.trazatex.quality.domain.Failure;
import com.nodotextil.trazatex.quality.domain.FailureDecision;
import com.nodotextil.trazatex.quality.domain.InvalidQualityControlException;
import com.nodotextil.trazatex.quality.domain.event.FailureConfirmedEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public final class ManageFailureUseCase {

    private final FailureRepository failureRepository;
    private final BatchQualityRepository batchQualityRepository;
    private final ProductionQualityPort productionQualityPort;
    private final QualityEventPublisher eventPublisher;

    public ManageFailureUseCase(
            FailureRepository failureRepository,
            BatchQualityRepository batchQualityRepository,
            ProductionQualityPort productionQualityPort,
            QualityEventPublisher eventPublisher) {

        this.failureRepository = Objects.requireNonNull(failureRepository);
        this.batchQualityRepository = Objects.requireNonNull(batchQualityRepository);
        this.productionQualityPort = Objects.requireNonNull(productionQualityPort);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Transactional
    public Failure execute(
            UUID failureId,
            String cause,
            BigDecimal affectedQuantityKg,
            String observations,
            FailureDecision decision) {

        Objects.requireNonNull(failureId, "Failure id is required");
        Objects.requireNonNull(decision, "Failure decision is required");

        Failure failure = failureRepository.findById(failureId)
                .orElseThrow(() -> new InvalidQualityControlException(
                        "Failure was not found"
                ));

        BatchQuality batchQuality = batchQualityRepository.findByBatchId(failure.getBatchId())
                .orElseThrow(() -> new InvalidQualityControlException(
                        "Quality information was not found for the batch"
                ));

        if (affectedQuantityKg == null
                || affectedQuantityKg.compareTo(productionQualityPort.quantityOf(failure.getBatchId())) > 0) {
            throw new InvalidQualityControlException(
                    "Affected quantity cannot exceed the batch quantity");
        }

        failure.completeDetails(cause, affectedQuantityKg, observations);

        switch (decision) {
            case REEVALUATE -> sendToReevaluation(failure, batchQuality);
            case DISCARD -> discardBatch(failure);
        }

        batchQualityRepository.save(batchQuality);
        return failureRepository.save(failure);
    }

    private void sendToReevaluation(Failure failure, BatchQuality batchQuality) {
        failure.sendToReevaluation();
        batchQuality.requestReevaluation();
    }

    private void discardBatch(Failure failure) {
        failure.confirm();
        productionQualityPort.discardBatch(failure.getBatchId());

        eventPublisher.publish(new FailureConfirmedEvent(
                UUID.randomUUID(),
                failure.getId(),
                failure.getBatchId(),
                LocalDateTime.now()
        ));
    }
}
