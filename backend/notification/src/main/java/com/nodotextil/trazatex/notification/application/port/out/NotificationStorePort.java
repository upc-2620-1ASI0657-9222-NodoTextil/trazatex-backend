package com.nodotextil.trazatex.notification.application.port.out;

import com.nodotextil.trazatex.notification.application.dto.NotificationPage;
import com.nodotextil.trazatex.notification.application.dto.NotificationView;
import com.nodotextil.trazatex.notification.domain.model.NotificationNotice;
import com.nodotextil.trazatex.notification.domain.model.NotificationRecipient;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface NotificationStorePort {
    boolean claim(UUID eventId,String type);
    void projectUser(UUID user,UUID company,String role,String email,String status,OffsetDateTime at);
    void save(NotificationNotice notice);
    int enqueueEmails(NotificationNotice notice);
    void enqueue(UUID event,String email,String subject,String body,Instant expires);
    NotificationPage list(NotificationRecipient recipient,Boolean unread,String type,int page,int size);
    Optional<NotificationView> find(UUID id,NotificationRecipient recipient);
    long unreadCount(NotificationRecipient recipient);
    void setRead(UUID id,boolean value,NotificationRecipient recipient);
    void readAll(NotificationRecipient recipient);
}
