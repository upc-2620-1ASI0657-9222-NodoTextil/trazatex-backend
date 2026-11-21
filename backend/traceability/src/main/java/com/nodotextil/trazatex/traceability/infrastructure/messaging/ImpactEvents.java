package com.nodotextil.trazatex.traceability.infrastructure.messaging;

import java.util.List;
import java.util.UUID;

final class ImpactEvents {

    static final String POSSIBLE_DERIVED_FAILURE_DETECTED = "PossibleDerivedFailureDetected";
    static final String AGGREGATE_TYPE = "FailureImpact";

    private ImpactEvents() {
    }

    record Affected(UUID batchId, UUID companyId, UUID commonAncestorBatchId) {
    }

    record PossibleDerivedFailureDetected(UUID failureId, UUID sourceBatchId, List<Affected> affected) {
    }
}
