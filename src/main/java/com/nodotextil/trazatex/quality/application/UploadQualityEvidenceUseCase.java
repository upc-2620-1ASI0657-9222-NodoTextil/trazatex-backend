package com.nodotextil.trazatex.quality.application;

import org.springframework.transaction.annotation.Transactional;
import com.nodotextil.trazatex.quality.application.contract.QualityEvidenceStorage;
import com.nodotextil.trazatex.quality.application.port.FailureRepository;
import com.nodotextil.trazatex.quality.application.port.QualityControlRepository;
import com.nodotextil.trazatex.quality.application.port.QualityEvidenceRepository;
import com.nodotextil.trazatex.quality.domain.EvidenceOwnerType;
import com.nodotextil.trazatex.quality.domain.InvalidQualityControlException;
import com.nodotextil.trazatex.quality.domain.QualityEvidence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
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

    @Transactional
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

        validateImage(content, contentType);

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


    @Transactional(readOnly = true)
    public List<QualityEvidence> list(EvidenceOwnerType ownerType, UUID ownerId) {
        validateOwnerExists(ownerType, ownerId);
        return evidenceRepository.findByOwner(ownerType, ownerId);
    }

    private static void validateImage(byte[] content, String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new InvalidQualityControlException("Evidence content type is required");
        }
        String type = contentType.toLowerCase(Locale.ROOT);
        boolean valid = switch (type) {
            case "image/jpeg" -> content.length >= 3
                    && Byte.toUnsignedInt(content[0]) == 0xFF
                    && Byte.toUnsignedInt(content[1]) == 0xD8
                    && Byte.toUnsignedInt(content[2]) == 0xFF;
            case "image/png" -> content.length >= 8
                    && Byte.toUnsignedInt(content[0]) == 0x89
                    && content[1] == 'P' && content[2] == 'N' && content[3] == 'G';
            case "image/gif" -> content.length >= 6
                    && content[0] == 'G' && content[1] == 'I' && content[2] == 'F';
            case "image/webp" -> content.length >= 12
                    && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                    && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
            default -> false;
        };
        if (!valid) {
            throw new InvalidQualityControlException("Only valid JPEG, PNG, GIF, or WEBP images are supported");
        }
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
