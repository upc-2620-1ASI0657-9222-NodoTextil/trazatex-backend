package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.EvidenceItems;
import com.nodotextil.trazatex.quality.application.dto.EvidenceUpload;
import com.nodotextil.trazatex.quality.domain.model.EvidenceOwnerType;

import java.util.List;
import java.util.UUID;

/** US43: fotos de un control o de una falla. Cada empresa ve solo las evidencias que ella subió. */
public interface ManageEvidenceUseCase {

    /**
     * Valida todos los archivos (todo o nada), los sube a Cloudinary y, si Cloudinary no responde, los deja {@code PENDING} en la
     * cola local: la petición termina igual, sin error. <b>No bloquea el lote ni la transformación</b> mientras dura la subida.
     */
    EvidenceItems attach(EvidenceOwnerType ownerType, UUID ownerId, List<EvidenceUpload> files, Actor actor);

    EvidenceItems list(EvidenceOwnerType ownerType, UUID ownerId, Actor actor);
}
