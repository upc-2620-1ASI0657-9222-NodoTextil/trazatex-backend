package com.nodotextil.trazatex.quality.infrastructure.web;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.EvidenceItems;
import com.nodotextil.trazatex.quality.application.dto.EvidenceUpload;
import com.nodotextil.trazatex.quality.application.dto.Role;
import com.nodotextil.trazatex.quality.application.port.in.ManageEvidenceUseCase;
import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.quality.domain.model.EvidenceOwnerType;
import com.nodotextil.trazatex.quality.infrastructure.web.security.RequiresRole;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * US43: fotos de un control o de una falla. Adjuntar y listar son solo del {@code COMPANY_ADMIN} de la empresa dueña (el control puede estar en
 * cualquier estado); cada empresa ve solo las evidencias que subió ella.
 */
@RestController
class EvidenceController {

    private final ManageEvidenceUseCase evidence;
    private final com.nodotextil.trazatex.quality.application.port.in.DeleteEvidenceUseCase deletions;

    EvidenceController(ManageEvidenceUseCase evidence, com.nodotextil.trazatex.quality.application.port.in.DeleteEvidenceUseCase deletions) {
        this.evidence = evidence;
        this.deletions = deletions;
    }

    /** Exactamente uno de {@code controlId} / {@code failureId}, como parámetro de la misma petición multipart (campo del formulario o de la URL). */
    @PostMapping(value = "/api/quality/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole(Role.COMPANY_ADMIN)
    EvidenceItems attach(@RequestParam(required = false) UUID controlId, @RequestParam(required = false) UUID failureId,
                         @RequestPart("files") List<MultipartFile> files, Actor actor) {
        if ((controlId == null) == (failureId == null)) {
            throw new ValidationException(ErrorCodes.EVIDENCE_OWNER_REQUIRED, "Indique exactamente uno de \"controlId\" o \"failureId\".", null);
        }
        return controlId != null ? evidence.attach(EvidenceOwnerType.CONTROL, controlId, uploads(files), actor)
                : evidence.attach(EvidenceOwnerType.FAILURE, failureId, uploads(files), actor);
    }

    @GetMapping("/api/quality/controls/{id}/evidence")
    @RequiresRole(Role.COMPANY_ADMIN)
    EvidenceItems listOfControl(@PathVariable UUID id, Actor actor) {
        return evidence.list(EvidenceOwnerType.CONTROL, id, actor);
    }

    @GetMapping("/api/quality/failures/{id}/evidence")
    @RequiresRole(Role.COMPANY_ADMIN)
    EvidenceItems listOfFailure(@PathVariable UUID id, Actor actor) {
        return evidence.list(EvidenceOwnerType.FAILURE, id, actor);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/api/quality/controls/{id}/evidence/{evidenceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresRole(Role.COMPANY_ADMIN)
    void deleteControl(@PathVariable UUID id, @PathVariable UUID evidenceId, Actor actor) {
        deletions.delete(EvidenceOwnerType.CONTROL, id, evidenceId, actor);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/api/quality/failures/{id}/evidence/{evidenceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresRole(Role.COMPANY_ADMIN)
    void deleteFailure(@PathVariable UUID id, @PathVariable UUID evidenceId, Actor actor) {
        deletions.delete(EvidenceOwnerType.FAILURE, id, evidenceId, actor);
    }

    private static List<EvidenceUpload> uploads(List<MultipartFile> files) {
        try {
            List<EvidenceUpload> uploads = new ArrayList<>();
            for (MultipartFile file : files) {
                uploads.add(new EvidenceUpload(file.getOriginalFilename(), file.getBytes()));
            }
            return uploads;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
