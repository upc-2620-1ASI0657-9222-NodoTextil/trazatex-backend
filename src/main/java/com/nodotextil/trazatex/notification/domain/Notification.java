package com.nodotextil.trazatex.notification.domain;
import java.time.LocalDateTime;
import java.util.UUID;
public record Notification(UUID id, UUID recipientUserId, NotificationType type, String title,
        String message, boolean read, LocalDateTime createdAt) {
    public Notification markRead() { return new Notification(id, recipientUserId, type, title, message, true, createdAt); }
}
