package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.EvidenceItems;
import com.nodotextil.trazatex.production.application.dto.EvidenceUpload;
import com.nodotextil.trazatex.production.application.dto.Role;
import com.nodotextil.trazatex.production.application.port.in.ManageEvidenceUseCase;
import com.nodotextil.trazatex.production.domain.model.EvidenceOwnerType;
import com.nodotextil.trazatex.production.infrastructure.web.security.RequiresRole;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.nodotextil.trazatex.production.application.port.in.DeleteEvidenceUseCase;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

/**
 * US42: fotos de un lote o de una transformación. Adjuntar es del {@code OPERATOR} de la empresa dueña; listar, de su
 * {@code OPERATOR} y su {@code COMPANY_ADMIN}. Cada empresa ve solo las evidencias que subió ella.
 */
@RestController
class EvidenceController {

    private final ManageEvidenceUseCase evidence;

    private final DeleteEvidenceUseCase deletions;

    EvidenceController(ManageEvidenceUseCase evidence, DeleteEvidenceUseCase deletions) {
        this.evidence = evidence;
        this.deletions = deletions;
    }

    @PostMapping(value = "/api/batches/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole(Role.OPERATOR)
    EvidenceItems attachToBatch(@PathVariable UUID id, @RequestPart("files") List<MultipartFile> files, Actor actor) {
        return evidence.attach(EvidenceOwnerType.BATCH, id, uploads(files), actor);
    }

    @GetMapping("/api/batches/{id}/evidence")
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    EvidenceItems listOfBatch(@PathVariable UUID id, Actor actor) {
        return evidence.list(EvidenceOwnerType.BATCH, id, actor);
    }

    @PostMapping(value = "/api/transformations/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole(Role.OPERATOR)
    EvidenceItems attachToTransformation(@PathVariable UUID id, @RequestPart("files") List<MultipartFile> files, Actor actor) {
        return evidence.attach(EvidenceOwnerType.TRANSFORMATION, id, uploads(files), actor);
    }

    @GetMapping("/api/transformations/{id}/evidence")
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    EvidenceItems listOfTransformation(@PathVariable UUID id, Actor actor) {
        return evidence.list(EvidenceOwnerType.TRANSFORMATION, id, actor);
    }

    @DeleteMapping("/api/batches/{id}/evidence/{evidenceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresRole(Role.OPERATOR)
    void deleteBatchEvidence(@PathVariable UUID id, @PathVariable UUID evidenceId, Actor actor) {
        deletions.delete(EvidenceOwnerType.BATCH, id, evidenceId, actor);
    }

    @DeleteMapping("/api/transformations/{id}/evidence/{evidenceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresRole(Role.OPERATOR)
    void deleteTransformationEvidence(@PathVariable UUID id, @PathVariable UUID evidenceId, Actor actor) {
        deletions.delete(EvidenceOwnerType.TRANSFORMATION, id, evidenceId, actor);
    }

    private static List<EvidenceUpload> uploads(List<MultipartFile> files) {
        try {
            java.util.ArrayList<EvidenceUpload> uploads = new java.util.ArrayList<>();
            for (MultipartFile file : files) {
                uploads.add(new EvidenceUpload(file.getOriginalFilename(), file.getBytes()));
            }
            return uploads;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
