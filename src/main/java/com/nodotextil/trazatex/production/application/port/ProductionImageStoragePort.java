package com.nodotextil.trazatex.production.application.port;

import com.nodotextil.trazatex.production.domain.ProductionEvidenceOwnerType;
import java.util.UUID;

public interface ProductionImageStoragePort {

    StoredImage store(
            ProductionEvidenceOwnerType ownerType,
            UUID ownerId,
            byte[] content,
            String filename,
            String contentType);

    record StoredImage(String url, String publicId) {
    }
}
