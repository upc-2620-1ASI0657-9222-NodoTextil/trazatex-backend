package com.nodotextil.trazatex.quality.infrastructure.external;

import com.nodotextil.trazatex.quality.application.contract.QualityEvidenceStorage;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.external-services.enabled", havingValue = "false", matchIfMissing = true)
public class DevQualityEvidenceStorageAdapter implements QualityEvidenceStorage {

    @Override
    public StoredEvidence upload(String fileName, String contentType, byte[] content) {
        String publicId = "dev/quality/" + UUID.randomUUID();
        return new StoredEvidence(publicId, "dev://" + publicId);
    }
}
