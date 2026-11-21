package com.nodotextil.trazatex.quality.application.port.out;

import java.util.UUID;

public record PhaseCommand(UUID batchId, PhaseOperation operation, String requestId, Boolean qualityHold, String reason, UUID requestedBy) {

    public static PhaseCommand startEvaluation(UUID batchId, UUID controlId, UUID requestedBy) {
        return new PhaseCommand(batchId, PhaseOperation.START_EVALUATION, controlId.toString(), null, null, requestedBy);
    }
}
