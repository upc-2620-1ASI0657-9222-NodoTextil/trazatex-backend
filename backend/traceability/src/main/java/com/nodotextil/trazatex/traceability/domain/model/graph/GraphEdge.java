package com.nodotextil.trazatex.traceability.domain.model.graph;

import com.nodotextil.trazatex.traceability.domain.model.EdgeType;

import java.util.UUID;

public record GraphEdge(EdgeType type, UUID from, UUID to) {

    public GraphEdge {
        if (!type.isGenealogical()) {
            throw new IllegalArgumentException(type + " no es una relación genealógica");
        }
    }
}
