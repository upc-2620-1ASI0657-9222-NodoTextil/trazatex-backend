package com.nodotextil.trazatex.notification.application.dto;

/** Lo que el proveedor de correo recibe. {@code body} puede llevar un secreto (token de invitación): no se registra en ningún log. */
public record OutboundEmail(String to,String subject,String body) {
    @Override public String toString() { return "OutboundEmail[to=" + to + ", subject=" + subject + ", body=***]"; }
}
