package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.EvidenceItems;
import com.nodotextil.trazatex.quality.application.dto.EvidenceUpload;
import com.nodotextil.trazatex.quality.domain.model.EvidenceOwnerType;

import java.util.List;
import java.util.UUID;

public interface ManageEvidenceUseCase {

    EvidenceItems attach(EvidenceOwnerType ownerType, UUID ownerId, List<EvidenceUpload> files, Actor actor);

    EvidenceItems list(EvidenceOwnerType ownerType, UUID ownerId, Actor actor);
}
