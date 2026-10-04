package com.nodotextil.trazatex.notification.application.port;
import com.nodotextil.trazatex.notification.domain.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface NotificationRepository {
    Notification save(Notification notification);
    Optional<Notification> findById(UUID id);
    List<Notification> findByRecipientUserId(UUID recipientUserId);
}