package com.nodotextil.trazatex.notification.domain.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Una notificación por evento y destinatario (usuario concreto, o todos los de {@code role} en {@code companyId}). {@code data} lleva los
 * ids que la interfaz necesita (transferencia, solicitud, falla, lotes); {@code title} y {@code message} son texto genérico en español.
 */
public record NotificationNotice(UUID eventId,UUID userId,UUID companyId,String role,NotificationType type,
        String title,String message,String resource,String resourceId,Map<String,Object> data,OffsetDateTime occurredAt) {
    public NotificationNotice {
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
