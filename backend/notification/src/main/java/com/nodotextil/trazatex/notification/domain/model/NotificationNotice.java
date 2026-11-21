package com.nodotextil.trazatex.notification.domain.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record NotificationNotice(UUID eventId,UUID userId,UUID companyId,String role,NotificationType type,
        String title,String message,String resource,String resourceId,Map<String,Object> data,OffsetDateTime occurredAt) {
    public NotificationNotice {
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
