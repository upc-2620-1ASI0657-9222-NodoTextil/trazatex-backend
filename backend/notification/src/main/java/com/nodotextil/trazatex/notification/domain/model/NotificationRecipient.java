package com.nodotextil.trazatex.notification.domain.model;

import java.util.UUID;

public record NotificationRecipient(UUID userId,UUID companyId,String role) { }
