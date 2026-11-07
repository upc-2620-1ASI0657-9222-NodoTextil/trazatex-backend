package com.nodotextil.trazatex.traceability.domain.service;

import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;
import com.nodotextil.trazatex.traceability.domain.model.graph.BatchData;

import java.util.UUID;

/**
 * Las reglas del análisis de impacto de una falla confirmada (US33, docs/domain/traceability.md), sin recorrido ni base de datos.
 * <ul>
 *   <li><b>Qué se marca</b>: un lote completo de la familia con {@code phase = AVAILABLE}, que no sea el lote fallado ni tenga {@code qualityStatus = FAILED}
 *       (decisión del usuario, 2026-10-21: regla literal). El estado {@code DISCARDED} es una fase y ya queda fuera por no ser {@code AVAILABLE}. Los demás estados de
 *       calidad ({@code POTENTIAL_DERIVED_FAILURE}, {@code PENDING_REEVALUATION}, {@code CONFORMING}, {@code NOT_REVIEWED}) y la retención {@code qualityHold} <b>no</b> excluyen.
 *       Los lotes históricos ({@code PROCESSED}, {@code SPLIT}, {@code DISCARDED}) solo sirven para reconstruir la familia.</li>
 *   <li><b>{@code commonAncestorBatchId}</b>: entre los puntos de partida desde los que se llega al afectado (el lote fallado y sus antecesores), el más cercano al lote
 *       fallado (menor nivel hacia atrás; el propio lote fallado es el nivel 0); en un empate, el de menor {@code batchId} (texto del UUID canónico).</li>
 * </ul>
 */
public final class ImpactRule {

    private ImpactRule() {
    }

    public static boolean isAffected(BatchData batch, UUID sourceBatchId) {
        return batch.isComplete()
                && !batch.batchId().equals(sourceBatchId)
                && batch.phase() == BatchPhase.AVAILABLE
                && batch.qualityStatus() != QualityStatus.FAILED;
    }

    /** ¿Es el punto de partida {@code a} más cercano al lote fallado que {@code b}? Primero el menor nivel hacia atrás; en un empate, el menor {@code batchId}. */
    public static boolean isCloserToTheFailedBatch(int levelOfA, UUID a, int levelOfB, UUID b) {
        if (levelOfA != levelOfB) {
            return levelOfA < levelOfB;
        }
        return a.toString().compareTo(b.toString()) < 0;
    }
}
