package com.nodotextil.trazatex.sharedmessaging.consumer;

/**
 * El evento es válido pero no se puede aplicar y reintentarlo no lo arregla (p. ej. referencia un lote que no existe): va
 * directo a {@code <topic>.DLT}, sin reintentos. Quien la lanza debe registrar antes el motivo (nivel ERROR). Los fallos
 * transitorios (base de datos caída, conflicto de versión) <b>no</b> son esto: se reintentan con espera creciente.
 */
public class NonRetryableEventException extends RuntimeException {

    public NonRetryableEventException(String message) {
        super(message);
    }

    public NonRetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
