package com.nodotextil.trazatex.quality.application.contract;

public interface QualityEvidenceStorage {

    StoredEvidence upload(String fileName, String contentType, byte[] content);

    record StoredEvidence(String publicId, String url) {
    }
}
