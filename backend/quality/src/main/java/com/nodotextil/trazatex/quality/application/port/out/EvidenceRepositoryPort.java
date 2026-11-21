package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.model.Evidence;
import com.nodotextil.trazatex.quality.domain.model.EvidenceOwnerType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvidenceRepositoryPort {

    record NewEvidence(Evidence evidence, byte[] queuedContent) {
    }

    record PendingUpload(Evidence evidence, byte[] content) {
    }

    void saveAll(List<NewEvidence> evidences);

    List<Evidence> findByOwnerAndCompany(EvidenceOwnerType ownerType, UUID ownerId, UUID companyId);

    Optional<PendingUpload> claimNextDue(Instant now, Instant leaseUntil);

    void update(Evidence evidence);
}
