package com.nodotextil.trazatex.notification.infrastructure.persistence;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
interface SpringDataNotificationRepository extends JpaRepository<NotificationJpaEntity, UUID> {
    List<NotificationJpaEntity> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId);
}