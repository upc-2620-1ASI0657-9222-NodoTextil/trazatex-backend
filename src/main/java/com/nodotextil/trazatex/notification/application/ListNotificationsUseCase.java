package com.nodotextil.trazatex.notification.application;
import com.nodotextil.trazatex.notification.application.port.NotificationRepository;
import com.nodotextil.trazatex.notification.domain.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ListNotificationsUseCase {
    private final NotificationRepository repository;
    public ListNotificationsUseCase(NotificationRepository repository) { this.repository = repository; }
    public List<Notification> forUser(UUID userId) { return repository.findByRecipientUserId(userId); }
}