package com.nodotextil.trazatex.production.application.port.in;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.EvidenceItems;
import com.nodotextil.trazatex.production.application.dto.EvidenceUpload;
import com.nodotextil.trazatex.production.domain.model.EvidenceOwnerType;

import java.util.List;
import java.util.UUID;

/** US42: fotos de un lote o de una transformación. Cada empresa ve solo las evidencias que ella subió. */
public interface ManageEvidenceUseCase {

    /**
     * Valida todos los archivos (todo o nada), los sube a Cloudinary y, si Cloudinary no responde, los deja {@code PENDING} en la
     * cola local: la petición termina igual, sin error. <b>No bloquea el lote ni la transformación</b> mientras dura la subida.
     */
    EvidenceItems attach(EvidenceOwnerType ownerType, UUID ownerId, List<EvidenceUpload> files, Actor actor);

    EvidenceItems list(EvidenceOwnerType ownerType, UUID ownerId, Actor actor);
}
