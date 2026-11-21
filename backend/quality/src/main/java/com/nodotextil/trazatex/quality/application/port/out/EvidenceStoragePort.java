package com.nodotextil.trazatex.quality.application.port.out;

public interface EvidenceStoragePort {

    UploadedImage upload(String publicId, String filename, String contentType, byte[] content);

    default void delete(String publicId) {
        throw new com.nodotextil.trazatex.quality.application.exception.StorageNotAvailableException("El almacen no admite eliminacion.");
    }

    record UploadedImage(String url) {
    }
}
