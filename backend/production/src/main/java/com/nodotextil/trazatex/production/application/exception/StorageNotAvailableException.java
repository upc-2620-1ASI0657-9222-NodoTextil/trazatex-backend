package com.nodotextil.trazatex.production.application.exception;

/** El almacén de imágenes no se intentó: no está configurado o su circuito está abierto. No cuenta como intento fallido. */
public class StorageNotAvailableException extends RuntimeException {

    public StorageNotAvailableException(String message) {
        super(message, null, false, false);
    }
}
