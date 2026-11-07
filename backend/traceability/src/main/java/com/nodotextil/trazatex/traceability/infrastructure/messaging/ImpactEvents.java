package com.nodotextil.trazatex.traceability.infrastructure.messaging;

import java.util.List;
import java.util.UUID;

/** Los eventos que traceability produce (docs/architecture/communication.md). */
final class ImpactEvents {

    static final String POSSIBLE_DERIVED_FAILURE_DETECTED = "PossibleDerivedFailureDetected";
    static final String AGGREGATE_TYPE = "FailureImpact";

    private ImpactEvents() {
    }

    /** {@code affected[{batchId, companyId, commonAncestorBatchId}]}, ordenada por {@code batchId}. */
    record Affected(UUID batchId, UUID companyId, UUID commonAncestorBatchId) {
    }

    record PossibleDerivedFailureDetected(UUID failureId, UUID sourceBatchId, List<Affected> affected) {
    }
}
