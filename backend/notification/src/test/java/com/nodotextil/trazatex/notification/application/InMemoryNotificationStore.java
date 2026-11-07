package com.nodotextil.trazatex.notification.application;

import com.nodotextil.trazatex.notification.application.dto.NotificationPage;
import com.nodotextil.trazatex.notification.application.dto.NotificationView;
import com.nodotextil.trazatex.notification.application.port.out.NotificationStorePort;
import com.nodotextil.trazatex.notification.domain.model.NotificationNotice;
import com.nodotextil.trazatex.notification.domain.model.NotificationRecipient;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Doble en memoria del puerto: registra lo que el servicio guarda y encola, sin base de datos. */
class InMemoryNotificationStore implements NotificationStorePort {
    final Set<UUID> processed = new HashSet<>();
    final List<NotificationNotice> notices = new ArrayList<>();
    final List<NotificationNotice> emailed = new ArrayList<>();
    final List<String> invitations = new ArrayList<>();
    final List<String> projected = new ArrayList<>();
    final List<Instant> invitationExpiry = new ArrayList<>();

    @Override public boolean claim(UUID eventId, String type) { return processed.add(eventId); }
    @Override public void projectUser(UUID user, UUID company, String role, String email, String status, OffsetDateTime at) { projected.add(user + ":" + status); }
    @Override public void save(NotificationNotice notice) { notices.add(notice); }
    @Override public int enqueueEmails(NotificationNotice notice) { emailed.add(notice); return 1; }
    @Override public void enqueue(UUID event, String email, String subject, String body, Instant expires) { invitations.add(email + "|" + body); invitationExpiry.add(expires); }
    @Override public NotificationPage list(NotificationRecipient recipient, Boolean unread, String type, int page, int size) { throw new UnsupportedOperationException(); }
    @Override public Optional<NotificationView> find(UUID id, NotificationRecipient recipient) { throw new UnsupportedOperationException(); }
    @Override public long unreadCount(NotificationRecipient recipient) { throw new UnsupportedOperationException(); }
    @Override public void setRead(UUID id, boolean value, NotificationRecipient recipient) { throw new UnsupportedOperationException(); }
    @Override public void readAll(NotificationRecipient recipient) { throw new UnsupportedOperationException(); }
}
