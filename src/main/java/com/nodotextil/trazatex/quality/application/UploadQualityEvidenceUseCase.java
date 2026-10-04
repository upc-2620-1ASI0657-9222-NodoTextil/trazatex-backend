package com.nodotextil.trazatex.quality.application;

import com.nodotextil.trazatex.quality.application.contract.QualityEvidenceStorage;
import com.nodotextil.trazatex.quality.application.port.FailureRepository;
import com.nodotextil.trazatex.quality.application.port.QualityControlRepository;
import com.nodotextil.trazatex.quality.application.port.QualityEvidenceRepository;
import com.nodotextil.trazatex.quality.domain.EvidenceOwnerType;
import com.nodotextil.trazatex.quality.domain.InvalidQualityControlException;
import com.nodotextil.trazatex.quality.domain.QualityEvidence;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public final class UploadQualityEvidenceUseCase {

    private final QualityControlRepository qualityControlRepository;
    private final FailureRepository failureRepository;
    private final QualityEvidenceRepository evidenceRepository;
    private final QualityEvidenceStorage evidenceStorage;

    public UploadQualityEvidenceUseCase(
            QualityControlRepository qualityControlRepository,
            FailureRepository failureRepository,
            QualityEvidenceRepository evidenceRepository,
            QualityEvidenceStorage evidenceStorage) {

        this.qualityControlRepository = Objects.requireNonNull(qualityControlRepository);
        this.failureRepository = Objects.requireNonNull(failureRepository);
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository);
        this.evidenceStorage = Objects.requireNonNull(evidenceStorage);
    }

    public QualityEvidence execute(
            EvidenceOwnerType ownerType,
            UUID ownerId,
            String fileName,
            String contentType,
            byte[] content) {

        Objects.requireNonNull(ownerType, "Evidence owner type is required");
        Objects.requireNonNull(ownerId, "Evidence owner id is required");

        if (content == null || content.length == 0) {
            throw new InvalidQualityControlException("Evidence file is required");
        }

        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidQualityControlException(
                    "Quality evidence must be a valid image"
            );
        }

        validateOwnerExists(ownerType, ownerId);

        QualityEvidenceStorage.StoredEvidence stored = evidenceStorage.upload(
                fileName,
                contentType,
                content
        );

        QualityEvidence evidence = new QualityEvidence(
                UUID.randomUUID(),
                ownerType,
                ownerId,
                stored.publicId(),
                stored.url(),
                LocalDateTime.now()
        );

        return evidenceRepository.save(evidence);
    }

    private void validateOwnerExists(EvidenceOwnerType ownerType, UUID ownerId) {
        boolean exists = switch (ownerType) {
            case CONTROL -> qualityControlRepository.findById(ownerId).isPresent();
            case FAILURE -> failureRepository.findById(ownerId).isPresent();
        };

        if (!exists) {
            throw new InvalidQualityControlException(
                    "The control or failure associated with the evidence was not found"
            );
        }
    }
}
