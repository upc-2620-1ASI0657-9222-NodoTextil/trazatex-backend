package com.nodotextil.trazatex.quality.application.exception;

/** El almacén de imágenes respondió mal (timeout, red, 5xx, 429, credenciales): se reintenta con espera creciente. */
public class StorageTransientException extends RuntimeException {

    public StorageTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
