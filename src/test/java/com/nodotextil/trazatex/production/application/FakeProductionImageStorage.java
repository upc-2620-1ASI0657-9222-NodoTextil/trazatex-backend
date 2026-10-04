package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.application.port.ProductionImageStoragePort;
import com.nodotextil.trazatex.production.domain.ProductionEvidenceOwnerType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class FakeProductionImageStorage implements ProductionImageStoragePort {

    private final List<StoredRequest> requests = new ArrayList<>();

    @Override
    public StoredImage store(
            ProductionEvidenceOwnerType ownerType,
            UUID ownerId,
            byte[] content,
            String filename,
            String contentType) {
        requests.add(new StoredRequest(
                ownerType, ownerId, content.clone(), filename, contentType));
        return new StoredImage(
                "https://images.example/" + ownerId,
                "trazatex/production/" + ownerType.name().toLowerCase() + "/" + ownerId);
    }

    List<StoredRequest> requests() {
        return List.copyOf(requests);
    }

    record StoredRequest(
            ProductionEvidenceOwnerType ownerType,
            UUID ownerId,
            byte[] content,
            String filename,
            String contentType) {

        StoredRequest {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
