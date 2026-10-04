package com.nodotextil.trazatex.quality.application.port;

import com.nodotextil.trazatex.quality.domain.EvidenceOwnerType;
import com.nodotextil.trazatex.quality.domain.QualityEvidence;
import java.util.List;
import java.util.UUID;

public interface QualityEvidenceRepository {

    QualityEvidence save(QualityEvidence evidence);

    List<QualityEvidence> findByOwner(EvidenceOwnerType ownerType, UUID ownerId);
}
