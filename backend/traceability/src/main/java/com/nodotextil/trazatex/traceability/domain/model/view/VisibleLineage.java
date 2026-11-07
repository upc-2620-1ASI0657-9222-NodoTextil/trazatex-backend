package com.nodotextil.trazatex.traceability.domain.model.view;

import com.nodotextil.trazatex.traceability.domain.model.LineageDirection;

import java.util.List;
import java.util.UUID;

/**
 * La respuesta de una consulta de genealogía. {@code complete} es falso si el recorrido se detuvo en un nodo incompleto (el frontend
 * muestra "en actualización"); {@code truncated} es verdadero si había más genealogía pasado el límite de {@code maxLevels}.
 */
public record VisibleLineage(UUID batchId, LineageDirection direction, int maxLevels, boolean complete, boolean truncated,
                             List<VisibleNode> nodes, List<VisibleEdge> edges) implements VisibleView {
}
