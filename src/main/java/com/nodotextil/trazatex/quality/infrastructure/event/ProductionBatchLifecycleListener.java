package com.nodotextil.trazatex.quality.infrastructure.event;

import com.nodotextil.trazatex.production.application.event.BatchRegisteredEvent;
import com.nodotextil.trazatex.production.application.event.BatchSplitEvent;
import com.nodotextil.trazatex.production.application.event.TransformationCompletedEvent;
import com.nodotextil.trazatex.quality.application.InitializeBatchQualityUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class ProductionBatchLifecycleListener {

    private final InitializeBatchQualityUseCase initializeBatchQuality;

    ProductionBatchLifecycleListener(InitializeBatchQualityUseCase initializeBatchQuality) {
        this.initializeBatchQuality = initializeBatchQuality;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onBatchRegistered(BatchRegisteredEvent event) {
        initializeBatchQuality.execute(event.batchId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onBatchSplit(BatchSplitEvent event) {
        event.childBatchIds().forEach(initializeBatchQuality::execute);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onTransformationCompleted(TransformationCompletedEvent event) {
        event.outputBatchIds().forEach(initializeBatchQuality::execute);
    }
}
