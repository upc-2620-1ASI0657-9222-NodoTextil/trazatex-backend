package com.nodotextil.trazatex.notification.application.dto;

import java.util.UUID;

public record DueEmail(UUID id,String recipient,String subject,String body,int attempts) { }
