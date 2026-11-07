package com.nodotextil.trazatex.traceability.domain.model.graph;

import com.nodotextil.trazatex.traceability.domain.model.CompanyPrivacy;
import com.nodotextil.trazatex.traceability.domain.model.LineageDirection;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El resultado de recorrer la genealogía, <b>sin filtrar</b>: solo lotes y transformaciones completos, con su nivel (negativo hacia
 * atrás, positivo hacia adelante), las aristas entre ellos, el historial de transferencias (solo en la vista completa) y los nombres de
 * las empresas, y la privacidad B2B vigente de las empresas <b>ajenas</b> a quien consulta (las que no figuran son todo {@code PRIVATE}). {@code complete} es falso si el recorrido se detuvo en un nodo incompleto; {@code truncated} si había más genealogía
 * pasado el límite de {@value #MAX_LEVELS} niveles.
 */
public record Lineage(UUID startBatchId, LineageDirection direction, List<LeveledBatch> batches, List<LeveledTransformation> transformations,
                      List<GraphEdge> edges, Map<UUID, List<TransferRecord>> transfers, Map<UUID, String> companyNames,
                      Map<UUID, CompanyPrivacy> companyPrivacy, boolean complete, boolean truncated) {

    /** Niveles máximos en cada dirección (RNF-04). Un nivel es un salto de lote a lote. */
    public static final int MAX_LEVELS = 10;

    public record LeveledBatch(BatchData data, int level) {
    }

    public record LeveledTransformation(TransformationData data, int level) {
    }
}
