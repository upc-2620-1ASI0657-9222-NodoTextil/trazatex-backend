package com.nodotextil.trazatex.production.application.port.out;

public interface EvidenceStoragePort {

    UploadedImage upload(String publicId, String filename, String contentType, byte[] content);

    default void delete(String publicId) {
        throw new com.nodotextil.trazatex.production.application.exception.StorageNotAvailableException("El almacén no permite eliminar imágenes.");
    }

    record UploadedImage(String url) {
    }
}
