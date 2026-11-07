package com.nodotextil.trazatex.notification.infrastructure.web;

import com.nodotextil.trazatex.notification.application.NotificationInboxService;
import com.nodotextil.trazatex.notification.application.dto.NotificationPage;
import com.nodotextil.trazatex.notification.application.dto.NotificationView;
import com.nodotextil.trazatex.notification.domain.model.NotificationRecipient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
class NotificationController {
    private final NotificationInboxService inbox;
    NotificationController(NotificationInboxService inbox) { this.inbox=inbox; }

    /** Página de la bandeja (más recientes primero). {@code unread=true|false} filtra por lectura; {@code type} por código. */
    @GetMapping NotificationPage list(@RequestParam(required=false) Boolean unread,@RequestParam(required=false) String type,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,HttpServletRequest request) {
        return inbox.list(actor(request),unread,type,page,size);
    }
    @GetMapping("/unread-count") Map<String,Long> unreadCount(HttpServletRequest request) {
        return Map.of("count",inbox.unreadCount(actor(request)));
    }
    @PatchMapping("/{id}/read") NotificationView markRead(@PathVariable UUID id,HttpServletRequest request) {
        return inbox.setRead(id,true,actor(request));
    }
    @PutMapping("/{id}/read") void read(@PathVariable UUID id,@RequestParam(defaultValue="true") boolean value,HttpServletRequest request) {
        inbox.setRead(id,value,actor(request));
    }
    @PutMapping("/read-all") void readAll(HttpServletRequest request) { inbox.readAll(actor(request)); }
    private NotificationRecipient actor(HttpServletRequest request) {
        var a=RequestActor.from(request);
        return new NotificationRecipient(a.userId(),a.companyId(),a.role());
    }
}
