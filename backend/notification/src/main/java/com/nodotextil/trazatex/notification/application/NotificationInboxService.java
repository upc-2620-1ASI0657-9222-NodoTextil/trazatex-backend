package com.nodotextil.trazatex.notification.application;

import com.nodotextil.trazatex.notification.application.dto.NotificationPage;
import com.nodotextil.trazatex.notification.application.dto.NotificationView;
import com.nodotextil.trazatex.notification.application.port.out.NotificationStorePort;
import com.nodotextil.trazatex.notification.domain.model.NotificationRecipient;
import com.nodotextil.trazatex.notification.domain.model.NotificationType;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationInboxService {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    private final NotificationStorePort store;
    public NotificationInboxService(NotificationStorePort store) { this.store=store; }

    /** {@code size} por encima de {@value #MAX_SIZE} se recorta a {@value #MAX_SIZE}; {@code page < 0} o {@code size < 1} es {@code INVALID_PAGINATION}. */
    public NotificationPage list(NotificationRecipient a,Boolean unread,String type,int page,int size) {
        if (page < 0) throw new ValidationException("INVALID_PAGINATION","La página debe ser 0 o mayor.",List.of("page"));
        if (size < 1) throw new ValidationException("INVALID_PAGINATION","El tamaño de página debe ser 1 o mayor.",List.of("size"));
        String code = type == null || type.isBlank() ? null : NotificationType.parse(type).name();
        return store.list(a,unread,code,page,Math.min(size,MAX_SIZE));
    }
    public long unreadCount(NotificationRecipient a) { return store.unreadCount(a); }
    @Transactional public NotificationView setRead(UUID id,boolean value,NotificationRecipient a) {
        find(id,a);
        store.setRead(id,value,a);
        return find(id,a);
    }
    @Transactional public void readAll(NotificationRecipient a) { store.readAll(a); }
    private NotificationView find(UUID id,NotificationRecipient a) {
        return store.find(id,a).orElseThrow(()->new NotFoundException("NOTIFICATION_NOT_FOUND","La notificación no existe."));
    }
}
