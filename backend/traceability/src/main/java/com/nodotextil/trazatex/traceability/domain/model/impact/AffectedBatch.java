package com.nodotextil.trazatex.traceability.domain.model.impact;

import java.util.UUID;

/** Un lote que requiere revisión preventiva y el antecesor más cercano al lote fallado desde el que desciende ({@code commonAncestorBatchId}). */
public record AffectedBatch(UUID batchId, UUID companyId, UUID commonAncestorBatchId) {
}
