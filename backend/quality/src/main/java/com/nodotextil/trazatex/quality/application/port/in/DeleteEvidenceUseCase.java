package com.nodotextil.trazatex.quality.application.port.in;
import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.domain.model.EvidenceOwnerType;
import java.util.UUID;
public interface DeleteEvidenceUseCase {
    void delete(EvidenceOwnerType type, UUID ownerId, UUID evidenceId, Actor actor);
    int purgeDeleted();
}
