package com.nodotextil.trazatex.traceability.domain.exception;

import java.util.Set;
import java.util.UUID;

public class ImpactAnalysisIncompleteException extends RuntimeException {

    private final UUID sourceBatchId;
    private final Set<UUID> missingNodes;

    public ImpactAnalysisIncompleteException(UUID sourceBatchId, Set<UUID> missingNodes) {
        super("La familia del lote " + sourceBatchId + " aún no está completa: faltan " + missingNodes);
        this.sourceBatchId = sourceBatchId;
        this.missingNodes = Set.copyOf(missingNodes);
    }

    public UUID sourceBatchId() {
        return sourceBatchId;
    }

    public Set<UUID> missingNodes() {
        return missingNodes;
    }
}
