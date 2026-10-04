package com.nodotextil.trazatex.notification.application;
import com.nodotextil.trazatex.notification.application.port.NotificationRepository;
import com.nodotextil.trazatex.notification.domain.Notification;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarkNotificationAsReadUseCase {
    private final NotificationRepository repository;
    public MarkNotificationAsReadUseCase(NotificationRepository repository) { this.repository = repository; }
    @Transactional
    public Notification markRead(UUID id, UUID userId) {
        Notification notification = repository.findById(id)
                .filter(item -> item.recipientUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        return repository.save(notification.markRead());
    }
}