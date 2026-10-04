package com.nodotextil.trazatex.notification.interfaces.rest;

import com.nodotextil.trazatex.notification.application.CountUnreadNotificationsUseCase;
import com.nodotextil.trazatex.notification.application.ListNotificationsUseCase;
import com.nodotextil.trazatex.notification.application.MarkNotificationAsReadUseCase;
import com.nodotextil.trazatex.notification.domain.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final ListNotificationsUseCase listNotifications;
    private final MarkNotificationAsReadUseCase markAsRead;
    private final CountUnreadNotificationsUseCase countUnread;

    public NotificationController(ListNotificationsUseCase listNotifications,
                                   MarkNotificationAsReadUseCase markAsRead,
                                   CountUnreadNotificationsUseCase countUnread) {
        this.listNotifications = listNotifications;
        this.markAsRead = markAsRead;
        this.countUnread = countUnread;
    }

    @GetMapping
    public List<Notification> list(JwtAuthenticationToken auth) { return listNotifications.forUser(userId(auth)); }

    @PatchMapping("/{id}/read")
    public Notification read(@PathVariable UUID id, JwtAuthenticationToken auth) { return markAsRead.markRead(id, userId(auth)); }

    @GetMapping("/unread-count")
    public long unreadCount(JwtAuthenticationToken auth) { return countUnread.count(userId(auth)); }

    private UUID userId(JwtAuthenticationToken auth) { return UUID.fromString(auth.getToken().getClaimAsString("userId")); }
}