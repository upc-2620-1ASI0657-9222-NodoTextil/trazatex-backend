package com.nodotextil.trazatex.notification.infrastructure.persistence;
import com.nodotextil.trazatex.notification.domain.Notification;
import com.nodotextil.trazatex.notification.domain.NotificationType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "notification_notifications")
class NotificationJpaEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID recipientUserId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private NotificationType type;
    @Column(nullable = false) private String title;
    @Column(nullable = false, length = 2000) private String message;
    @Column(nullable = false) private boolean read;
    @Column(nullable = false) private LocalDateTime createdAt;
    protected NotificationJpaEntity() { }
    NotificationJpaEntity(Notification n) {
        id = n.id(); recipientUserId = n.recipientUserId(); type = n.type();
        title = n.title(); message = n.message(); read = n.read(); createdAt = n.createdAt();
    }
    Notification domain() { return new Notification(id, recipientUserId, type, title, message, read, createdAt); }
}