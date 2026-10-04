package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.application.port.ProductionImageStoragePort;
import com.nodotextil.trazatex.production.domain.BatchRepository;
import com.nodotextil.trazatex.production.domain.InvalidProductionEvidenceException;
import com.nodotextil.trazatex.production.domain.ProductionEvidence;
import com.nodotextil.trazatex.production.domain.ProductionEvidenceOwnerType;
import com.nodotextil.trazatex.production.domain.ProductionEvidenceRepository;
import com.nodotextil.trazatex.production.domain.TransformationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class ProductionEvidenceService {

    private static final String JPEG = "image/jpeg";
    private static final String PNG = "image/png";
    private static final String GIF = "image/gif";
    private static final String WEBP = "image/webp";

    private final ProductionEvidenceRepository evidenceRepository;
    private final ProductionImageStoragePort imageStorage;
    private final BatchRepository batchRepository;
    private final TransformationRepository transformationRepository;
    private final Clock clock;

    public ProductionEvidenceService(
            ProductionEvidenceRepository evidenceRepository,
            ProductionImageStoragePort imageStorage,
            BatchRepository batchRepository,
            TransformationRepository transformationRepository,
            Clock clock) {
        this.evidenceRepository = evidenceRepository;
        this.imageStorage = imageStorage;
        this.batchRepository = batchRepository;
        this.transformationRepository = transformationRepository;
        this.clock = clock;
    }

    @Transactional
    public ProductionEvidence upload(
            ProductionEvidenceOwnerType ownerType,
            UUID ownerId,
            UUID uploadedByUserId,
            byte[] content,
            String filename,
            String contentType) {
        validateOwner(ownerType, ownerId);
        Objects.requireNonNull(uploadedByUserId, "Uploader user id is required");
        String supportedContentType = validateImage(content, contentType);

        ProductionImageStoragePort.StoredImage storedImage = imageStorage.store(
                ownerType,
                ownerId,
                content.clone(),
                filename,
                supportedContentType);
        return evidenceRepository.save(new ProductionEvidence(
                UUID.randomUUID(),
                ownerType,
                ownerId,
                storedImage.url(),
                storedImage.publicId(),
                LocalDateTime.now(clock),
                uploadedByUserId));
    }

    @Transactional(readOnly = true)
    public List<ProductionEvidence> list(
            ProductionEvidenceOwnerType ownerType,
            UUID ownerId) {
        validateOwner(ownerType, ownerId);
        return evidenceRepository.findByOwner(ownerType, ownerId);
    }

    private void validateOwner(ProductionEvidenceOwnerType ownerType, UUID ownerId) {
        Objects.requireNonNull(ownerType, "Evidence owner type is required");
        Objects.requireNonNull(ownerId, "Evidence owner id is required");
        switch (ownerType) {
            case BATCH -> batchRepository.findById(ownerId)
                    .orElseThrow(() -> new BatchNotFoundException(ownerId));
            case TRANSFORMATION -> transformationRepository.findById(ownerId)
                    .orElseThrow(() -> new TransformationNotFoundException(ownerId));
        }
    }

    private static String validateImage(byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            throw new InvalidProductionEvidenceException("Evidence image is required");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new InvalidProductionEvidenceException("Evidence content type is required");
        }

        String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
        boolean supported = switch (normalizedContentType) {
            case JPEG -> isJpeg(content);
            case PNG -> isPng(content);
            case GIF -> isGif(content);
            case WEBP -> isWebp(content);
            default -> false;
        };
        if (!supported) {
            throw new InvalidProductionEvidenceException(
                    "Only valid JPEG, PNG, GIF, or WEBP images are supported");
        }
        return normalizedContentType;
    }

    private static boolean isJpeg(byte[] content) {
        return content.length >= 3
                && unsigned(content[0]) == 0xFF
                && unsigned(content[1]) == 0xD8
                && unsigned(content[2]) == 0xFF;
    }

    private static boolean isPng(byte[] content) {
        int[] signature = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (unsigned(content[index]) != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isGif(byte[] content) {
        return content.length >= 6
                && content[0] == 'G'
                && content[1] == 'I'
                && content[2] == 'F'
                && content[3] == '8'
                && (content[4] == '7' || content[4] == '9')
                && content[5] == 'a';
    }

    private static boolean isWebp(byte[] content) {
        return content.length >= 12
                && content[0] == 'R'
                && content[1] == 'I'
                && content[2] == 'F'
                && content[3] == 'F'
                && content[8] == 'W'
                && content[9] == 'E'
                && content[10] == 'B'
                && content[11] == 'P';
    }

    private static int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }
}
