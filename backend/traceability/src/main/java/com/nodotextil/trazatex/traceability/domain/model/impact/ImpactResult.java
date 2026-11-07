package com.nodotextil.trazatex.traceability.domain.model.impact;

import java.util.List;
import java.util.UUID;

/**
 * El resultado del análisis de impacto de una falla confirmada: los lotes afectados (de cualquier empresa, ordenados por {@code batchId}) y si la familia seguía
 * más allá del límite de niveles. Es un dato interno: no pasa por la política de privacidad y solo sale por el evento {@code PossibleDerivedFailureDetected}.
 */
public record ImpactResult(UUID sourceBatchId, List<AffectedBatch> affected, boolean truncated) {

    public ImpactResult {
        affected = List.copyOf(affected);
    }
}
