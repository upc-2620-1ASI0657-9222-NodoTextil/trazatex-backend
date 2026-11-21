package com.nodotextil.trazatex.production.domain.event;

import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.BatchPhaseChange;

import java.util.UUID;

public record BatchPhaseChanged(
        UUID batchId,
        UUID companyId,
        BatchPhase previousPhase,
        BatchPhase newPhase,
        boolean qualityHold,
        String reason,
        String requestId,
        long batchVersion) implements ProductionEvent {

    public static final String TYPE = "BatchPhaseChanged";

    public static BatchPhaseChanged of(BatchPhaseChange change, String reason, String requestId, long batchVersion) {
        return new BatchPhaseChanged(change.batchId(), change.companyId(), change.previousPhase(), change.newPhase(),
                change.qualityHold(), reason, requestId, batchVersion);
    }

    @Override
    public String eventType() {
        return TYPE;
    }

    @Override
    public String aggregateId() {
        return batchId.toString();
    }
}
