package com.nodotextil.trazatex.production.infrastructure.storage;

import com.nodotextil.trazatex.production.application.port.ProductionImageStoragePort;
import com.nodotextil.trazatex.production.domain.ProductionEvidenceOwnerType;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.external-services.enabled", havingValue = "false", matchIfMissing = true)
class DevProductionImageStorageAdapter implements ProductionImageStoragePort {

    @Override
    public StoredImage store(
            ProductionEvidenceOwnerType ownerType,
            UUID ownerId,
            byte[] content,
            String filename,
            String contentType) {
        String publicId = "dev/production/" + ownerType.name().toLowerCase() + "/" + ownerId + "/" + UUID.randomUUID();
        return new StoredImage("dev://" + publicId, publicId);
    }
}
