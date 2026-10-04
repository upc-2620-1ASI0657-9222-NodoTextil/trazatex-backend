package com.nodotextil.trazatex.traceability.infrastructure.event;

import com.nodotextil.trazatex.quality.domain.event.FailureConfirmedEvent;
import com.nodotextil.trazatex.traceability.application.AnalyzeFailureImpactUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class QualityEventListener {

    private final AnalyzeFailureImpactUseCase analyzeFailureImpactUseCase;

    QualityEventListener(AnalyzeFailureImpactUseCase analyzeFailureImpactUseCase) {
        this.analyzeFailureImpactUseCase = analyzeFailureImpactUseCase;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onFailureConfirmed(FailureConfirmedEvent event) {
        analyzeFailureImpactUseCase.execute(event.failureId(), event.batchId());
    }
}
