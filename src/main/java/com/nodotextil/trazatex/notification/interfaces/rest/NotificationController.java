package com.nodotextil.trazatex.notification.interfaces.rest;

import com.nodotextil.trazatex.notification.application.CountUnreadNotificationsUseCase;
import com.nodotextil.trazatex.notification.application.ListNotificationsUseCase;
import com.nodotextil.trazatex.notification.application.MarkNotificationAsReadUseCase;
import com.nodotextil.trazatex.notification.domain.Notification;
import com.nodotextil.trazatex.shared.security.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasAnyRole('LICENSE_OWNER','COMPANY_ADMIN','OPERATOR')")
public class NotificationController {

    private final ListNotificationsUseCase listNotifications;
    private final MarkNotificationAsReadUseCase markAsRead;
    private final CountUnreadNotificationsUseCase countUnread;

    public NotificationController(
            ListNotificationsUseCase listNotifications,
            MarkNotificationAsReadUseCase markAsRead,
            CountUnreadNotificationsUseCase countUnread) {
        this.listNotifications = listNotifications;
        this.markAsRead = markAsRead;
        this.countUnread = countUnread;
    }

    @GetMapping
    public List<Notification> list(@AuthenticationPrincipal Jwt jwt) {
        return listNotifications.forUser(AuthenticatedUser.from(jwt).userId());
    }

    @PatchMapping("/{id}/read")
    public Notification read(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return markAsRead.markRead(id, AuthenticatedUser.from(jwt).userId());
    }

    @GetMapping("/unread-count")
    public long unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return countUnread.count(AuthenticatedUser.from(jwt).userId());
    }
}
