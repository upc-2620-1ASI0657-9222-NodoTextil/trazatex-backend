package com.nodotextil.trazatex.quality.interfaces.rest;

import com.nodotextil.trazatex.quality.application.UploadQualityEvidenceUseCase;
import com.nodotextil.trazatex.quality.domain.EvidenceOwnerType;
import com.nodotextil.trazatex.quality.domain.InvalidQualityControlException;
import com.nodotextil.trazatex.quality.domain.QualityEvidence;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/quality/evidence")
public class QualityEvidenceController {

    private final UploadQualityEvidenceUseCase uploadEvidence;

    public QualityEvidenceController(UploadQualityEvidenceUseCase uploadEvidence) {
        this.uploadEvidence = uploadEvidence;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public QualityEvidence upload(
            @RequestParam EvidenceOwnerType ownerType,
            @RequestParam UUID ownerId,
            @RequestParam MultipartFile file) {

        try {
            return uploadEvidence.execute(
                    ownerType,
                    ownerId,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()
            );
        } catch (IOException exception) {
            throw new InvalidQualityControlException(
                    "The evidence file could not be read"
            );
        }
    }
}
