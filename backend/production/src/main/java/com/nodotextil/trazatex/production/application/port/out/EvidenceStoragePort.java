package com.nodotextil.trazatex.production.application.port.out;

/**
 * Almacén de imágenes (Cloudinary), detrás de un puerto con timeout y circuit breaker (ADR-007). Subir es idempotente por
 * {@code publicId}: repetir la subida de la misma evidencia no duplica la imagen. Nunca simula éxito: solo devuelve la URL cuando
 * el servicio respondió 2xx con ella.
 */
public interface EvidenceStoragePort {

    /**
     * @throws com.nodotextil.trazatex.production.application.exception.StorageNotAvailableException no se intentó (sin configurar, circuito abierto)
     * @throws com.nodotextil.trazatex.production.application.exception.StorageTransientException    respondió mal: reintentar luego
     * @throws com.nodotextil.trazatex.production.application.exception.StoragePermanentException    rechazó el archivo: no reintentar
     */
    UploadedImage upload(String publicId, String filename, String contentType, byte[] content);

    default void delete(String publicId) {
        throw new com.nodotextil.trazatex.production.application.exception.StorageNotAvailableException("El almacén no permite eliminar imágenes.");
    }

    record UploadedImage(String url) {
    }
}
