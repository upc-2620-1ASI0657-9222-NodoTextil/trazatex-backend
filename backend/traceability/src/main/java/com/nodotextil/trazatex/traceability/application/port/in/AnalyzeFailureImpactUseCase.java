package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.application.dto.AnalyzeFailureImpactCommand;

/**
 * Caso de uso del evento {@code FailureConfirmed} (US33): el listener lo llama dentro de la transacción idempotente. Calcula la familia del lote fallado y deja
 * {@code PossibleDerivedFailureDetected} en el outbox (en esa misma transacción) si hay lotes afectados. Si la familia aún no está completa lanza
 * {@link com.nodotextil.trazatex.traceability.domain.exception.ImpactAnalysisIncompleteException} (reintentable: no deja nada).
 */
public interface AnalyzeFailureImpactUseCase {

    void apply(AnalyzeFailureImpactCommand command);
}
