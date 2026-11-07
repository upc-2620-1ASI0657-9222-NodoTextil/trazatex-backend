package com.nodotextil.trazatex.notification.application.port.out;

import com.nodotextil.trazatex.notification.application.dto.OutboundEmail;

/** Envío de un correo por un proveedor externo (ADR-007). Los fallos se traducen a {@link EmailDeliveryException}. */
public interface EmailSenderPort {
    /** Falso cuando falta la configuración del proveedor: los correos quedan {@code PENDING} y no se envía nada. */
    boolean isConfigured();

    /** @throws EmailDeliveryException si el proveedor no aceptó el correo. */
    void send(OutboundEmail email);
}
