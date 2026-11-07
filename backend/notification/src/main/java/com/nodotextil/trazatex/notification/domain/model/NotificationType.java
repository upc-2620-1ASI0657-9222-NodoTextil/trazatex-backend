package com.nodotextil.trazatex.notification.domain.model;

import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import java.util.List;

/** Código estable del tipo de una notificación. La interfaz decide cómo mostrarlo: el servicio nunca expone texto de pantalla en {@code type}. */
public enum NotificationType {
    ADMIN_REQUEST_SUBMITTED,
    ADMIN_REQUEST_RESOLVED,
    TRANSFER_RECEIVED,
    TRANSFER_ACCEPTED,
    TRANSFER_REJECTED,
    POTENTIAL_DERIVED_FAILURE;

    /** @throws ValidationException {@code INVALID_PARAMETER} si el código no existe. */
    public static NotificationType parse(String code) {
        try {
            return valueOf(code.trim());
        } catch (RuntimeException e) {
            throw new ValidationException("INVALID_PARAMETER", "El tipo de notificación no existe.", List.of("type"));
        }
    }
}
