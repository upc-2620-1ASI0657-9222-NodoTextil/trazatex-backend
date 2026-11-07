package com.nodotextil.trazatex.notification.application.port.out;

/** El proveedor no aceptó el correo. {@code reason} es un código corto, sin cuerpo ni token: se guarda en {@code last_error}. */
public class EmailDeliveryException extends RuntimeException {
    public enum Kind {
        /** Reintentar puede funcionar (429, 5xx, tiempo agotado, red). Cuenta como intento. */
        TRANSIENT,
        /** Reintentar no lo arregla (4xx: clave inválida, remitente sin verificar, petición rechazada). Pasa a {@code FAILED}. */
        PERMANENT,
        /** El circuito está abierto: no se llamó al proveedor. No cuenta como intento. */
        UNAVAILABLE
    }

    private final Kind kind;
    private final String reason;

    public EmailDeliveryException(Kind kind, String reason, Throwable cause) {
        super(reason, cause);
        this.kind = kind;
        this.reason = reason;
    }
    public Kind kind() { return kind; }
    public String reason() { return reason; }
}
