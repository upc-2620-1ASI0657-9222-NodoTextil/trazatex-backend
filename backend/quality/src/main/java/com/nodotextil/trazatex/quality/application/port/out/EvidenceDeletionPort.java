package com.nodotextil.trazatex.quality.application.port.out;
import com.nodotextil.trazatex.quality.domain.model.EvidenceOwnerType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
public interface EvidenceDeletionPort {
    boolean markDeleted(EvidenceOwnerType type, UUID ownerId, UUID evidenceId, UUID companyId, Instant now);
    Optional<UUID> nextDeleted(Instant now);
    void purge(UUID id);
    /** La purga de esta evidencia falló: cuenta el intento y la aplaza con espera creciente, para que no frene a las demás. */
    void deferPurge(UUID id, Instant now);
}
