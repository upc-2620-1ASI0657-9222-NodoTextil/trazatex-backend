package com.nodotextil.trazatex.production.application.port.in;
import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.domain.model.EvidenceOwnerType;
import java.util.UUID;
public interface DeleteEvidenceUseCase {
    void delete(EvidenceOwnerType type, UUID ownerId, UUID evidenceId, Actor actor);
    int purgeDeleted();
}
