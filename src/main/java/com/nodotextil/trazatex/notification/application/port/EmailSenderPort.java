package com.nodotextil.trazatex.notification.application.port;
public interface EmailSenderPort {
    void send(String recipient, String subject, String message);
}