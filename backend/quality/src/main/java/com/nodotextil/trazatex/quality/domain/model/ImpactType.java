package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.util.UUID;

public enum ImpactType {
    ANCESTOR,
    DESCENDANT,
    RELATED_BRANCH;

    public static ImpactType derive(UUID sourceBatchId, UUID batchId, UUID commonAncestorBatchId) {
        if (sourceBatchId == null || batchId == null || commonAncestorBatchId == null) {
            throw new ValidationException(ErrorCodes.INVALID_IMPACT,
                    "El aviso debe traer el lote fallado, el lote afectado y su antecesor común", java.util.List.of());
        }
        if (commonAncestorBatchId.equals(batchId)) {
            return ANCESTOR;
        }
        return commonAncestorBatchId.equals(sourceBatchId) ? DESCENDANT : RELATED_BRANCH;
    }
}
