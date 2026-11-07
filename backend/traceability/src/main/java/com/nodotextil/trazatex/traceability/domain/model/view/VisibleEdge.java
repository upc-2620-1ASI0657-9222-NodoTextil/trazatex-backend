package com.nodotextil.trazatex.traceability.domain.model.view;

import com.nodotextil.trazatex.traceability.domain.model.EdgeType;

import java.util.UUID;

/** Una relación genealógica: siempre se muestra (la privacidad oculta atributos de un nodo, no sus relaciones). */
public record VisibleEdge(EdgeType type, UUID from, UUID to) implements VisibleView {
}
