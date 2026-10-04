package com.nodotextil.trazatex.notification.infrastructure.persistence;
import com.nodotextil.trazatex.notification.application.port.NotificationRepository;
import com.nodotextil.trazatex.notification.domain.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class NotificationPersistenceAdapter implements NotificationRepository {
    private final SpringDataNotificationRepository repository;
    NotificationPersistenceAdapter(SpringDataNotificationRepository repository) { this.repository = repository; }
    @Override public Notification save(Notification notification) { return repository.save(new NotificationJpaEntity(notification)).domain(); }
    @Override public Optional<Notification> findById(UUID id) { return repository.findById(id).map(NotificationJpaEntity::domain); }
    @Override public List<Notification> findByRecipientUserId(UUID recipientUserId) {
        return repository.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId).stream().map(NotificationJpaEntity::domain).toList();
    }
}