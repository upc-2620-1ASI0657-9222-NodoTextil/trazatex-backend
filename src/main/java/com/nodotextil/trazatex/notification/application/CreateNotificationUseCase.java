package com.nodotextil.trazatex.notification.application;
import com.nodotextil.trazatex.notification.application.port.NotificationRepository;
import com.nodotextil.trazatex.notification.domain.Notification;
import com.nodotextil.trazatex.notification.domain.NotificationType;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CreateNotificationUseCase {
    private final NotificationRepository repository;
    public CreateNotificationUseCase(NotificationRepository repository) { this.repository = repository; }
    public Notification create(UUID recipientUserId, NotificationType type, String title, String message) {
        return repository.save(new Notification(UUID.randomUUID(), recipientUserId, type, title, message, false, LocalDateTime.now()));
    }
}