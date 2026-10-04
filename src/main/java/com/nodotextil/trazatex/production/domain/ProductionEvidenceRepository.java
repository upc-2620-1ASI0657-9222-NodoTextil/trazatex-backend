package com.nodotextil.trazatex.production.domain;

import java.util.List;
import java.util.UUID;

public interface ProductionEvidenceRepository {

    ProductionEvidence save(ProductionEvidence evidence);

    List<ProductionEvidence> findByOwner(
            ProductionEvidenceOwnerType ownerType,
            UUID ownerId);
}
