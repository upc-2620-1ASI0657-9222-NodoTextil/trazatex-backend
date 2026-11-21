package com.nodotextil.trazatex.traceability.domain.model.view;

import com.nodotextil.trazatex.traceability.domain.model.EdgeType;

import java.util.UUID;

public record VisibleEdge(EdgeType type, UUID from, UUID to) implements VisibleView {
}
