package com.nodotextil.trazatex.quality.infrastructure.event;

import com.nodotextil.trazatex.quality.application.MarkPotentialDerivedFailureUseCase;
import com.nodotextil.trazatex.traceability.application.event.PossibleDerivedFailureDetectedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class DerivedFailureEventListener {

    private final MarkPotentialDerivedFailureUseCase markPotentialDerivedFailure;

    DerivedFailureEventListener(MarkPotentialDerivedFailureUseCase markPotentialDerivedFailure) {
        this.markPotentialDerivedFailure = markPotentialDerivedFailure;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onPossibleDerivedFailure(PossibleDerivedFailureDetectedEvent event) {
        event.affectedBatchIds().forEach(markPotentialDerivedFailure::execute);
    }
}
