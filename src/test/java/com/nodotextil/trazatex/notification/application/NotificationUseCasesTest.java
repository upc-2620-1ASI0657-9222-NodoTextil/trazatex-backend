package com.nodotextil.trazatex.notification.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nodotextil.trazatex.notification.application.port.NotificationRepository;
import com.nodotextil.trazatex.notification.domain.Notification;
import com.nodotextil.trazatex.notification.domain.NotificationType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationUseCasesTest {

    private NotificationRepository repository;
    private UUID user, other, id;
    private Notification notification;

    @BeforeEach
    void setup() {
        repository = mock(NotificationRepository.class);
        user = UUID.randomUUID(); other = UUID.randomUUID(); id = UUID.randomUUID();
        notification = new Notification(id, user, NotificationType.TRANSFER_PENDING, "title", "message", false, LocalDateTime.now());
    }

    @Test void listUsesOnlyReturnOwnNotifications() {
        when(repository.findByRecipientUserId(user)).thenReturn(List.of(notification));
        assertThat(new ListNotificationsUseCase(repository).forUser(user)).containsExactly(notification);
    }

    @Test void userCannotMarkAnotherUsersNotificationRead() {
        when(repository.findById(id)).thenReturn(Optional.of(notification));
        assertThatThrownBy(() -> new MarkNotificationAsReadUseCase(repository).markRead(id, other))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test void userCanMarkOwnNotificationRead() {
        when(repository.findById(id)).thenReturn(Optional.of(notification));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(new MarkNotificationAsReadUseCase(repository).markRead(id, user).read()).isTrue();
    }

    @Test void unreadCountOnlyCountsUnreadNotifications() {
        Notification read = notification.markRead();
        when(repository.findByRecipientUserId(user)).thenReturn(List.of(notification, read));
        assertThat(new CountUnreadNotificationsUseCase(repository).count(user)).isEqualTo(1);
    }

    @Test void createPersistsUnreadNotification() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        Notification created = new CreateNotificationUseCase(repository).create(user, NotificationType.INVITATION, "t", "m");
        assertThat(created.read()).isFalse();
        verify(repository).save(any());
    }
}