package com.nodotextil.trazatex.quality.application.exception;

/** El almacén de imágenes rechazó el archivo (no es una imagen válida): reintentarlo no lo arregla. */
public class StoragePermanentException extends RuntimeException {

    public StoragePermanentException(String message) {
        super(message, null, false, false);
    }
}
