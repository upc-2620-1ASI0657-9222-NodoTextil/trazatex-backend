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
    /**
     * Crea el correo de {@code notice} para cada usuario {@code ACTIVE} que cumple el destinatario <b>ahora</b>: el destinatario se resuelve
     * al crear la notificación (un usuario reactivado después no recibe avisos antiguos). Se llama en la misma transacción que {@link #save}.
     */
    int enqueueEmails(NotificationNotice notice);
    /** Un correo a una dirección que no es un usuario (invitación). */
    void enqueue(UUID event,String email,String subject,String body,Instant expires);
    /** @param unread {@code true} solo no leídas, {@code false} solo leídas, {@code null} todas; {@code type} código o {@code null}. */
    NotificationPage list(NotificationRecipient recipient,Boolean unread,String type,int page,int size);
    Optional<NotificationView> find(UUID id,NotificationRecipient recipient);
    long unreadCount(NotificationRecipient recipient);
    void setRead(UUID id,boolean value,NotificationRecipient recipient);
    void readAll(NotificationRecipient recipient);
}
