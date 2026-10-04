package com.nodotextil.trazatex.traceability.domain;

import java.util.UUID;

public interface LineageRepository {

    void saveEdge(LineageEdge edge);

    LineageGraph findAncestors(UUID batchId);

    LineageGraph findDescendants(UUID batchId);

    LineageGraph findFamily(UUID batchId);
}
