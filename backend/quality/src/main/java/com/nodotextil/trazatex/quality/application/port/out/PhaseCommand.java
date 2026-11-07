package com.nodotextil.trazatex.quality.application.port.out;

import java.util.UUID;

/**
 * El comando interno Quality → Production. {@code requestId} (controlId o failureId) es lo que hace seguro repetirlo: Production es
 * idempotente por {@code (batchId, requestId, operation)}.
 */
public record PhaseCommand(UUID batchId, PhaseOperation operation, String requestId, Boolean qualityHold, String reason, UUID requestedBy) {

    /** {@code START_EVALUATION} de un control: {@code requestId} es el id del control. */
    public static PhaseCommand startEvaluation(UUID batchId, UUID controlId, UUID requestedBy) {
        return new PhaseCommand(batchId, PhaseOperation.START_EVALUATION, controlId.toString(), null, null, requestedBy);
    }
}
