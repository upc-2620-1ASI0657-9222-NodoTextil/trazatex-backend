package com.nodotextil.trazatex.notification.application;
import com.nodotextil.trazatex.notification.application.port.NotificationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CountUnreadNotificationsUseCase {
    private final NotificationRepository repository;
    public CountUnreadNotificationsUseCase(NotificationRepository repository) { this.repository = repository; }
    public long count(UUID userId) { return repository.findByRecipientUserId(userId).stream().filter(n -> !n.read()).count(); }
}