package com.nodotextil.trazatex.notification.application.dto;

import java.util.Map;
import java.util.UUID;

public record NotificationView(UUID id,String type,String title,String message,String resourceType,String resourceId,
        Map<String,Object> data,UUID companyId,String role,String createdAt,boolean read) { }
