package com.nodotextil.trazatex.notification.application.dto;

import java.util.UUID;

/** Un correo pendiente que el job tomó para enviar; {@code attempts} son los intentos ya consumidos. */
public record DueEmail(UUID id,String recipient,String subject,String body,int attempts) { }
