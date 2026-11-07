package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.util.UUID;

/**
 * Cómo se relaciona el lote afectado con la falla, deducido del {@code commonAncestorBatchId} que Traceability calcula
 * (docs/domain/traceability.md: el punto de partida más cercano al lote fallado desde el que se llega al afectado; el propio lote
 * fallado es el nivel 0).
 */
public enum ImpactType {
    /** El lote afectado es el antecesor común: es un antecesor del lote fallado (llega aquí solo con datos atrasados, ver quality.md). */
    ANCESTOR,
    /** El antecesor común es el lote fallado: el afectado es su derivado. */
    DESCENDANT,
    /** Cualquier otro: cuelga de un antecesor del lote fallado, en otra rama. */
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
