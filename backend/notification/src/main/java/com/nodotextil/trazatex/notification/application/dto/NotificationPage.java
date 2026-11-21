package com.nodotextil.trazatex.notification.application.dto;

import java.util.List;

public record NotificationPage(List<NotificationView> items,int page,int size,long totalElements,int totalPages) {
    public static NotificationPage of(List<NotificationView> items,int page,int size,long total) {
        return new NotificationPage(items,page,size,total,(int)Math.ceil(total/(double)size));
    }
}
