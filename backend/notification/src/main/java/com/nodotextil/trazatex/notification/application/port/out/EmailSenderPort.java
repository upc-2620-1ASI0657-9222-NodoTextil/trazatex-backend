package com.nodotextil.trazatex.notification.application.port.out;

import com.nodotextil.trazatex.notification.application.dto.OutboundEmail;

public interface EmailSenderPort {
    boolean isConfigured();

    void send(OutboundEmail email);
}
