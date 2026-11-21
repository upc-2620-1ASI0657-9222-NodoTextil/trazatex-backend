package com.nodotextil.trazatex.production.application.port.in;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.EvidenceItems;
import com.nodotextil.trazatex.production.application.dto.EvidenceUpload;
import com.nodotextil.trazatex.production.domain.model.EvidenceOwnerType;

import java.util.List;
import java.util.UUID;

public interface ManageEvidenceUseCase {

    EvidenceItems attach(EvidenceOwnerType ownerType, UUID ownerId, List<EvidenceUpload> files, Actor actor);

    EvidenceItems list(EvidenceOwnerType ownerType, UUID ownerId, Actor actor);
}
