package com.nodotextil.trazatex.traceability.domain.exception;

import java.util.Set;
import java.util.UUID;

/**
 * La familia del lote fallado todavía tiene nodos provisionales o incompletos (los eventos de Production que los completan pueden llegar después de
 * {@code FailureConfirmed}) o el propio lote fallado aún no está completo: un resultado ahora sería parcial y se prefiere no publicar nada. <b>Es reintentable</b>:
 * a propósito no es una {@code TrazatexException} ni una {@code IllegalArgumentException} (el consumidor manda esas a la DLT sin reintentar), así que el evento se
 * reintenta con la espera del consumidor y solo va a la DLT al agotarla.
 */
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

    /** Los nodos (lotes o transformaciones) provisionales o incompletos, o el propio lote fallado si él es el que falta. */
    public Set<UUID> missingNodes() {
        return missingNodes;
    }
}
