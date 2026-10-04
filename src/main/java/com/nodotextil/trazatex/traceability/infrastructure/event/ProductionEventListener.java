package com.nodotextil.trazatex.traceability.infrastructure.event;

import com.nodotextil.trazatex.production.application.event.BatchSplitEvent;
import com.nodotextil.trazatex.production.application.event.TransformationCompletedEvent;
import com.nodotextil.trazatex.traceability.application.RecordLineageUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class ProductionEventListener {

    private final RecordLineageUseCase recordLineageUseCase;

    ProductionEventListener(RecordLineageUseCase recordLineageUseCase) {
        this.recordLineageUseCase = recordLineageUseCase;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onBatchSplit(BatchSplitEvent event) {
        recordLineageUseCase.recordDivision(
                event.parentBatchId(), event.childBatchIds(), event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onTransformationCompleted(TransformationCompletedEvent event) {
        recordLineageUseCase.recordTransformation(
                event.inputBatchIds(), event.outputBatchIds(), event.occurredAt());
    }
}
